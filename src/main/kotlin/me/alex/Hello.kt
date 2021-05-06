package me.alex

import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent


import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import net.dv8tion.jda.api.requests.GatewayIntent
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.io.*
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import javax.imageio.ImageIO
import kotlin.collections.ArrayList

val pdfs = ArrayList<LoadedPDF>()
const val timeout: Long = 6000 * 100
const val rightArrow = "U+27A1"
const val leftArrow = "U+2B05"
const val cdnUserId = "838947834870366238"
var jda: JDA? = null
fun main() {

    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    jda = JDABuilder.createDefault("").enableIntents(GatewayIntent.GUILD_MEMBERS)
            .setEventManager(AnnotatedEventManager()).addEventListeners(object : Any() {
                @SubscribeEvent
                fun onMessageReceived(e: MessageReceivedEvent) {
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
                @SubscribeEvent
                fun onReact(e: MessageReactionAddEvent) {
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
                                        sendEmbedOnReaction(page + 1, pdf, e, true)
                                    }
                                } else if (e.reactionEmote.asCodepoints.toUpperCase() == leftArrow) {
                                    if (page - 1 >= 0) {
                                        sendEmbedOnReaction(page - 1, pdf, e, false)
                                    }
                                }
                            }
                        }
                    }
                }
            }).build().awaitReady()
    /*(jda.getGuildChannelById("814085706241671179")as TextChannel?)?.retrieveMessageById("838889596624175174")?.queue {
        println(it.contentRaw)
    }*/
}

fun sendEmbed(it: InputStream, name: String, e: MessageReceivedEvent) = runBlocking {
    launch(context = Dispatchers.IO) {
        val safeData = ArrayList<Byte>()
        var data = 0
        while (data != -1 && safeData.size < 8389000) {
            data = it.read()
            safeData.add(data.toByte())
        }

        ByteArrayOutputStream().use { out ->
            val doc = PDDocument.load(safeData.toByteArray() + it.readAllBytes())
            ImageIO.write(PDFRenderer(doc).renderImageWithDPI(0,100f) ,"png", out)
            e.jda.openPrivateChannelById(cdnUserId).queue { ch ->
                ch.sendFile(out.toByteArray(), "$name.png").queue { cdnMsg ->
                    e.channel.sendMessage(EmbedBuilder().setTitle("Loaded $name").setColor(Color.GREEN).setImage(cdnMsg.attachments[0].url).setFooter("1").build()).queue {
                        val pdf = LoadedPDF(doc, e.author.name, System.currentTimeMillis(), it)
                        if (pdf.cachedEmbeds.size != 1) {
                            it.addReaction(rightArrow).queue()
                        }
                        pdfs.add(pdf)
                        cache(1, pdf)
                        pdf.expiry = System.currentTimeMillis() + timeout
                        clean(System.currentTimeMillis() + timeout, pdf)
                    }
                }
            }
        }
        try {
            it.close()
        } catch (ex: IOException) {}
    }
}

fun sendEmbedOnReaction(index: Int, pdf: LoadedPDF, e: MessageReactionAddEvent, cacheNext: Boolean) = runBlocking {
    launch(context = Dispatchers.IO) {
        if (pdf.cachedEmbeds[index].build().image == null) {
            val baos = ByteArrayOutputStream()
            ImageIO.write(PDFRenderer(pdf.pdf).renderImageWithDPI(index, 100f), "png", baos)
            e.jda.openPrivateChannelById(cdnUserId).queue { chn ->
                chn.sendFile(baos.toByteArray(), pdf.formatAuthor + ".png").queue { cdn ->
                    val embed = pdf.cachedEmbeds[index].setImage(cdn.attachments[0].url).build()
                    e.channel.editMessageById(e.messageId, embed).queue { final ->
                        addReaction(pdf, final, index)
                    }
                }
            }
        } else {
            e.channel.editMessageById(e.messageId, pdf.cachedEmbeds[index].build()).queue { final ->
                addReaction(pdf, final, index)
            }
        }
        if (cacheNext) {
            cache(index, pdf)
        }
    }
}
fun addReaction(pdf: LoadedPDF, final: Message, index: Int) {
    if (index + 1 < pdf.cachedEmbeds.size) {
        final.addReaction(rightArrow).queue()
    }
    if (index - 1 >= 0) {
        final.addReaction(leftArrow).queue()
    }
    pdf.expiry = System.currentTimeMillis() + timeout
    pdf.page = index
    clean(System.currentTimeMillis() + timeout, pdf)
}
fun cache(index: Int, pdf: LoadedPDF) {
    if (index >= 0 && index < pdf.cachedEmbeds.size) {
        val out = ByteArrayOutputStream()
        ImageIO.write(PDFRenderer(pdf.pdf).renderImageWithDPI(index, 100f),"png", out)
        jda!!.openPrivateChannelById(cdnUserId).queue {
            it.sendFile(out.toByteArray(), pdf.formatAuthor + ".png").queue { final ->
                pdf.cachedEmbeds[index].setImage(final.attachments[0].url)
            }
        }
    }
}
fun clean(targetTime: Long, pdf: LoadedPDF) = runBlocking {
    launch {
        delay(targetTime - System.currentTimeMillis())
        if (pdf.expiry  <= System.currentTimeMillis()) { // pdf expired so delete otherwise just leave it alone
            pdfs.remove(pdf)
        }
    }
}




