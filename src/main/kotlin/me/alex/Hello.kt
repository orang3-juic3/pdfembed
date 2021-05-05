package me.alex

import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent


import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
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
const val textEmote = "U+1F4DD"
var jda: JDA? = null
fun main() {
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    jda = JDABuilder.createDefault("")
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
                    runBlocking {
                        matches.map { URL(it) }.forEach {
                            launch(context = Dispatchers.IO) {
                                try {
                                    val name = it.toString()
                                    it.openStream()?.use {
                                        sendEmbed(it, name, e)
                                    }

                                } catch (ex: IOException) {}
                            }
                        }
                        e.message.attachments.forEach {
                            launch(context = Dispatchers.IO) {
                                if (it.fileExtension == "pdf") {
                                    try {
                                        val name = it.fileName
                                        it.retrieveInputStream().get()?.use {
                                            sendEmbed(it, name, e)
                                        }
                                    } catch (ex: IOException) {}
                                }
                            }
                        }
                    }
                }
            @SubscribeEvent
            fun onReact(e: MessageReactionAddEvent) {
                val pdf = pdfs.find { it.ids.contains(e.messageId) }
                pdf?.run {
                    e.retrieveUser().queue { user ->
                        if (user.isBot) return@queue
                        if (!e.reactionEmote.isEmoji) return@queue
                        e.channel.retrieveMessageById(e.messageId).queue { msg ->
                            val edit = msg.reactions.parallelStream()
                                    .filter { it.reactionEmote.asCodepoints.toUpperCase() == textEmote}
                                    .filter { !it.isSelf || (it.hasCount() && it.count >= 2) }
                                    .count() > 0
                            if (e.reactionEmote.asCodepoints.toUpperCase() == rightArrow) {
                                val index = Integer.parseInt(msg.embeds[0].footer!!.text!!)
                                if (index < pdf.cachedEmbeds.size) {
                                    sendEmbedOnReaction(index, pdf, e, true, edit)
                                }
                            } else if (e.reactionEmote.asCodepoints.toUpperCase() == leftArrow) {
                                val index = Integer.parseInt(msg.embeds[0].footer!!.text!!) - 2
                                if (index >= 0) {
                                    sendEmbedOnReaction(index, pdf, e, false, edit)
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

fun sendEmbed(it: InputStream, name: String, e: MessageReceivedEvent) {
    val safeData = ArrayList<Byte>()
    var data = 0
    while (data != -1 && safeData.size < 8389000) {
        data = it.read()
        safeData.add(data.toByte())
    }

    ByteArrayOutputStream().use { out ->
        val pdf = write(PDDocument.load(safeData.toByteArray() + it.readAllBytes()), out, e.author.name)
        e.jda.openPrivateChannelById(cdnUserId).queue { ch ->
            ch.sendFile(out.toByteArray(), "$name.png").queue { cdnMsg ->
                e.channel.sendMessage(EmbedBuilder().setTitle("Loaded $name").setColor(Color.GREEN).setImage(cdnMsg.attachments[0].url).setFooter("1").build()).queue {
                    pdf.ids.add(it.id)
                    if (pdf.cachedEmbeds.size != 1) {
                        it.addReaction(rightArrow).queue()
                    }
                    it.addReaction(textEmote).queue()
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
fun write(doc: PDDocument, out: OutputStream, author: String) : LoadedPDF {
    ImageIO.write(PDFRenderer(doc).renderImageWithDPI(0,100f) ,"png", out)
    return LoadedPDF(System.currentTimeMillis() + timeout, doc,author)
}
fun sendEmbedOnReaction(index: Int, pdf: LoadedPDF, e: MessageReactionAddEvent, cacheNext: Boolean, edit : Boolean) {
    if (pdf.cachedEmbeds[index].build().image == null) {
        val baos = ByteArrayOutputStream()
        ImageIO.write(PDFRenderer(pdf.pdf).renderImageWithDPI(index, 100f), "png", baos)
        e.jda.openPrivateChannelById(cdnUserId).queue { chn ->
            chn.sendFile(baos.toByteArray(), pdf.formatAuthor + ".png").queue { cdn ->
                val embed = pdf.cachedEmbeds[index].setImage(cdn.attachments[0].url).build()
                if (!edit) {
                    e.channel.sendMessage(embed).queue { final ->
                        addReaction(pdf, final, index)
                    }
                } else {
                    e.channel.editMessageById(e.messageId, embed).queue { final ->
                        addReaction(pdf, final, index)
                    }
                }
            }
        }
    } else {
        if (!edit) {
            e.channel.sendMessage(pdf.cachedEmbeds[index].build()).queue { final ->
                addReaction(pdf, final, index)
            }
        } else {
            e.channel.editMessageById(e.messageId, pdf.cachedEmbeds[index].build()).queue { final ->
                addReaction(pdf, final, index)
            }
        }
    }
    if (cacheNext) {
        cache(index, pdf)
    }
}
fun addReaction(pdf: LoadedPDF, final: Message, index: Int) {
    pdf.ids.add(final.id)
    if (index + 1 < pdf.cachedEmbeds.size) {
        final.addReaction(rightArrow).queue()
    }
    if (index - 1 >= 0) {
        final.addReaction(leftArrow).queue()
    }
    final.addReaction(textEmote).queue()
    pdf.expiry = System.currentTimeMillis() + timeout
    clean(System.currentTimeMillis() + timeout, pdf)
}
fun cache(index: Int, pdf: LoadedPDF) = runBlocking {
    launch(context = Dispatchers.IO) {
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
}
fun clean(targetTime: Long, pdf: LoadedPDF) = runBlocking {
    launch {
        delay(targetTime - System.currentTimeMillis())
        if (pdf.expiry  <= System.currentTimeMillis()) { // pdf expired so delete otherwise just leave it alone
            pdfs.remove(pdf)
        }
    }
}



