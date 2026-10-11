package com.trainbooking.it25100977.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.trainbooking.it25100977.model.Ticket;
import java.io.IOException;
import java.nio.file.*;

/** Use an installed embeddable Unicode font when ticket data exceeds the standard font. */
final class TicketPdfFonts {
    private TicketPdfFonts() { }
    static PdfFont create(Ticket ticket, String standard) throws IOException {
        String data=String.join(" ",String.valueOf(ticket.getPassengerName()),String.valueOf(ticket.getTrainName()),
                String.valueOf(ticket.getOrigin()),String.valueOf(ticket.getDestination()),String.valueOf(ticket.getPassengerNic()));
        if(data.codePoints().allMatch(c->c<256)) return PdfFontFactory.createFont(standard);
        String configured=System.getProperty("trainbook.pdf.font","");
        String windows=System.getenv("WINDIR");
        Path path=configured.isBlank() ? Path.of(windows==null ? "C:/Windows" : windows,"Fonts","Nirmala.ttc") : Path.of(configured);
        if(!Files.isRegularFile(path)) throw new IOException("An embeddable Unicode font is required for these ticket details.");
        PdfFont font=path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ttc")
                ? PdfFontFactory.createTtcFont(path.toString(),StandardFonts.HELVETICA_BOLD.equals(standard)?1:0,
                    PdfEncodings.IDENTITY_H,PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED,true)
                : PdfFontFactory.createFont(path.toString(),PdfEncodings.IDENTITY_H,PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        if(data.codePoints().anyMatch(c->!Character.isWhitespace(c) && !font.containsGlyph(c))) {
            throw new IOException("The configured PDF font does not cover the ticket's characters.");
        }
        return font;
    }
}
