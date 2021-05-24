package me.alex

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import org.apache.pdfbox.pdmodel.PDDocument
import java.awt.Color

class LoadedPDF( val pdf: PDDocument, author: String, rateLimit: Long, var message: Message) {
    var expiry = System.currentTimeMillis() + timeout
    var rateLimit: Long = rateLimit
        private set
    var page = 0
    private val formatAuthor = "$author's pdf"
    val cachedEmbeds = Array(pdf.numberOfPages) {
        EmbedBuilder().setTitle("Page ${it + 1} | $formatAuthor").setFooter(("${it + 1}/${pdf.numberOfPages}").toString()).setColor(Color.GREEN)
    }
    fun updateRateLimit() {
        rateLimit = System.currentTimeMillis() +  1000
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other is LoadedPDF) {
            return other.message.id == this.message.id
        }
        return false
    }
    private val hashCode = (this::class.java.packageName + this::class.java.name).hashCode()
    override fun hashCode(): Int {
        return hashCode
    }

}