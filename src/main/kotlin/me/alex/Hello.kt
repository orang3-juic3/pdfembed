package me.alex

import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent


import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.io.*
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

val pdfs = HashSet<LoadedPDF>()
val service: ExecutorService = Executors.newCachedThreadPool()
const val timeout: Long = 1000 * 300
const val rightArrow = "U+27A1"
const val leftArrow = "U+2B05"
const val cdnUserId = "838947834870366238"
const val imgurClientId = "3fcee01e19cbfc4"
var jda: JDA? = null
var cdnChn: PrivateChannel? = null

fun main() {


    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    jda = JDABuilder.createDefault("")
            .setEventManager(AnnotatedEventManager()).addEventListeners(object : Any() {
                @SubscribeEvent
                fun onMessageReceived(e: MessageReceivedEvent) {
                    service.submit {
                        val pattern = Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,})")
                        val matcher = pattern.matcher(e.message.contentDisplay)
                        val matches = MutableList(0) { it.toString() }
                        while (matcher.find()) {
                            matches.add(matcher.group(0))
                        }
                        matches.replaceAll {
                            if (it.matches(Regex("https?://(www\\.)?arxiv.org/abs/[a-zA-Z0-9.]+"))) {
                                it.replace("abs", "pdf").replaceFirst("http://", "https://")
                            } else it
                        }
                        matches.map { URL(it) }.forEach { url ->
                            try {
                                val name = url.toString()
                                url.openStream()?.run {
                                    sendEmbed(this, name, e)
                                }

                            } catch (ex: IOException) {}
                        }
                        e.message.attachments.forEach { attachment ->
                            if (attachment.fileExtension == "pdf") {
                                try {
                                    val name = attachment.fileName
                                    attachment.retrieveInputStream().get()?.run {
                                        sendEmbed(this, name, e)
                                    }
                                } catch (ex: IOException) {}
                            }
                        }
                    }
                }
                @SubscribeEvent
                fun onReact(e: MessageReactionAddEvent) {
                    service.submit {
                        val pdf = pdfs.find { it.message.id == e.messageId }
                        pdf?.run {
                            val time =System.currentTimeMillis()
                            if (time < pdf.rateLimit) {
                                println("Ratelimited reaction")
                                return@run
                            }
                            if (e.reactionEmote.asCodepoints.toUpperCase() == rightArrow || e.reactionEmote.asCodepoints.toUpperCase() == leftArrow) {
                                e.retrieveUser().queue { user ->
                                    if (user.isBot) return@queue
                                    pdf.updateRateLimit()
                                    if (!e.reactionEmote.isEmoji) return@queue
                                    if (e.reactionEmote.asCodepoints.toUpperCase() == rightArrow) {
                                        if (page + 1 < pdf.cachedEmbeds.size) {
                                            sendEmbedOnReaction(page + 1, pdf)
                                        }
                                    } else if (e.reactionEmote.asCodepoints.toUpperCase() == leftArrow) {
                                        if (page - 1 >= 0) {
                                            sendEmbedOnReaction(page - 1, pdf)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }).build().awaitReady()
    cdnChn = jda!!.openPrivateChannelById(cdnUserId).complete()
    jda!!.presence.activity = Activity.watching("over ${jda!!.guilds.size} ${if (jda!!.guilds.size == 1) "server" else "servers"}!")
    /*(jda.getGuildChannelById("814085706241671179")as TextChannel?)?.retrieveMessageById("838889596624175174")?.queue {
        println(it.contentRaw)
    }*/
}

fun sendEmbed(it: InputStream, name: String, e: MessageReceivedEvent) {
    val safeData = ArrayList<Byte>()
    var data = 0
    while (data != -1 && safeData.size < 8389000) {
        data = it.read()
        safeData.add(data.toByte())
    }

    ByteArrayOutputStream().use { out ->
        val doc = PDDocument.load(ByteArrayInputStream(safeData.toByteArray() + it.readBytes()), MemoryUsageSetting.setupTempFileOnly())
        ImageIO.write(PDFRenderer(doc).renderImageWithDPI(0,100f) ,"png", out)
        ImgurRequest(imgurClientId, out.toByteArray()).queue { url ->
            e.channel.sendMessage(EmbedBuilder().setTitle("Loaded $name").setColor(Color.GREEN).setImage(url).build()).queue {
                val pdf = LoadedPDF(doc, e.author.name, System.currentTimeMillis(), it)
                pdfs.add(pdf)
                pdf.expiry = System.currentTimeMillis() + timeout
                clean(System.currentTimeMillis() + timeout, pdf)
                if (pdf.cachedEmbeds.size != 1) {
                    it.addReaction(rightArrow).queue()
                    cache(pdf, 1)
                }
            }
        }
        /*e.channel.sendFile(out.toByteArray(), "pdf.png").embed(EmbedBuilder().setTitle("Loaded $name").setColor(Color.GREEN).setImage("attachment://pdf.png").build()).queue {
            val pdf = LoadedPDF(doc, e.author.name, System.currentTimeMillis(), it)
            if (pdf.cachedEmbeds.size != 1) {
                it.addReaction(rightArrow).queue()
            }
            pdfs.add(pdf)
            pdf.expiry = System.currentTimeMillis() + timeout
            clean(System.currentTimeMillis() + timeout, pdf)
        }*/
    }
    try {
        it.close()
    } catch (ex: IOException) {}

}

fun sendEmbedOnReaction(index: Int, pdf: LoadedPDF) {
    ByteArrayOutputStream().use { baos ->
        val isCached = pdf.cachedEmbeds[index].build().image != null
        if (!isCached) {
            ImageIO.write(PDFRenderer(pdf.pdf).renderImageWithDPI(index, 100f), "png", baos)
            ImgurRequest(imgurClientId, baos.toByteArray()).queue { url ->
                val embed =pdf.cachedEmbeds[index].setImage(url).build()
                pdf.message.editMessage(embed).queue {
                    addReaction(pdf, it, index)
                }
            }
            cache(pdf, index)
        } else {
            pdf.message.editMessage(pdf.cachedEmbeds[index].build()).queue {
                addReaction(pdf, it, index)
            }
            cache(pdf,index)
        }
        clean(System.currentTimeMillis() + timeout, pdf)
    }
}

/**
 * Assumes index hasn't been incremented.
 */
fun cache(pdf: LoadedPDF, index: Int) {
    if (index + 1 >= pdf.cachedEmbeds.size || pdf.cachedEmbeds[index + 1].build().image != null) return
    ByteArrayOutputStream().use {baos ->
        ImageIO.write(PDFRenderer(pdf.pdf).renderImageWithDPI(index + 1, 100f), "png", baos)
        ImgurRequest(imgurClientId, baos.toByteArray()).queue {
            pdf.cachedEmbeds[index + 1].setImage(it)
        }
    }
}
fun addReaction(pdf: LoadedPDF, final: Message, index: Int) {
    if (index - 1 >= 0) {
        final.addReaction(leftArrow).queue()
    }
    if (index + 1 < pdf.cachedEmbeds.size) {
        final.addReaction(rightArrow).queue()
    }
    pdf.expiry = System.currentTimeMillis() + timeout
    pdf.page = index
    clean(System.currentTimeMillis() + timeout, pdf)
}
fun clean(targetTime: Long, pdf: LoadedPDF) {
    service.submit {
        System.gc()
        Thread.sleep(targetTime - System.currentTimeMillis())
        if (pdf.expiry  <= System.currentTimeMillis()) { // pdf expired so delete otherwise just leave it alone
            pdf.pdf.close()
            pdfs.remove(pdf)
        }
    }
}




