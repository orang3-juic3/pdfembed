package me.alex

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.Color

class LoadedPDF(var expiry:Long , val pdf: PDDocument, author: String ) {
    val formatAuthor = "$author's pdf"
    val cachedEmbeds = Array(pdf.numberOfPages) {
        EmbedBuilder().setTitle("Page ${it + 1} | ${formatAuthor}").setFooter((it + 1).toString()).setColor(Color.GREEN)
    }
    val ids = ArrayList<String>()
}