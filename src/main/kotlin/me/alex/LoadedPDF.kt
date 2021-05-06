package me.alex

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.Color

class LoadedPDF( val pdf: PDDocument, author: String, rateLimit: Long, val message: Message) {
    var expiry = System.currentTimeMillis() + timeout
    var rateLimit: Long = rateLimit
        private set
    var page = 0
    val formatAuthor = "$author's pdf"
    val cachedEmbeds = Array(pdf.numberOfPages) {
        EmbedBuilder().setTitle("Page ${it + 1} | $formatAuthor").setFooter((it + 1).toString()).setColor(Color.GREEN)
    }
    fun updateRateLimit() {
        rateLimit = System.currentTimeMillis() +  1000
    }
}