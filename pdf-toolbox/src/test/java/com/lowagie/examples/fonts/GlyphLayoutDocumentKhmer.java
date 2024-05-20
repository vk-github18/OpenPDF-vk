
/*
 * This code is part of the 'OpenPDF Tutorial'.
 * You can find the complete tutorial at the following address:
 * https://github.com/LibrePDF/OpenPDF/wiki/Tutorial
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *
 */
package com.lowagie.examples.fonts;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FopGlyphProcessor;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Prints bidirectional text with correct glyph layout, kerning and ligatures globally enabled
 */
public class GlyphLayoutDocumentKhmer {

    public static String INTRO_TEXT =
            "Test of bidirectional text\n" +
                    "Using LayoutProcessor for glyph layout with Java built-in routines.\n\n";

    /**
     * Main method
     *
     * @param args -- not used
     */
    public static void main(String[] args) throws Exception {
        test("GlyphLayoutDocumentKhmer.pdf");
    }

    /**
     * Run the test: Show bidirectional text
     *
     * @param fileName Name of output file
     * @throws Exception if an error occurs
     */
    public static void test(String fileName) throws Exception {

        System.out.println(FopGlyphProcessor.isFopSupported()?"fop is supported":"fop is NOT supported");

        // Enable the LayoutProcessor with kerning and ligatures
        LayoutProcessor.enableKernLiga();
        //LayoutProcessor.enable();
        //LayoutProcessor.setWriteActualText();

        System.out.println(LayoutProcessor.isEnabled()?"LayoutProcessor is enabled":"LayoutProcessor is NOT enabled");
        System.out.println("OpenPDF version=" + Document.getVersion());
        System.out.println("java.version="+System.getProperty("java.version"));

        float fontSize = 12.0f;

        // The  OpenType fonts loaded with FontFactory.register() are
        // available for glyph layout.
        String fontDir = "com/lowagie/examples/fonts/";

        FontFactory.register(fontDir + "noto/NotoSans-Regular.ttf", "notoSans");
        Font notoSans = FontFactory.getFont("notoSans", BaseFont.IDENTITY_H, true, fontSize);
        FontFactory.register(fontDir + "noto/NotoSansArabic-Regular.ttf", "notoSansArabic");
        Font notoSansArabic = FontFactory.getFont("notoSansArabic", BaseFont.IDENTITY_H, true, fontSize);

        FontFactory.register(fontDir + "khmer/Siemreap-Regular.ttf", "khmer");
        Font khmer = FontFactory.getFont("khmer", BaseFont.IDENTITY_H, true, fontSize);


        try (Document document = new Document()) {
            PdfWriter writer = PdfWriter.getInstance(document, Files.newOutputStream(Paths.get(fileName)));
            writer.setInitialLeading(16.0f);
            document.open();
/*            document.add(new Chunk(INTRO_TEXT + "Fonts: Noto Sans, Noto Sans Arabic\n\n", notoSans));

            document.add(new Chunk("Guten Tag ", notoSans));
            document.add(new Chunk("السلام عليكم", notoSansArabic));
            document.add(new Chunk(" Good afternoon", notoSans));
  */
            document.add(new Chunk("ហ៍្វ", khmer));
            document.add(new Paragraph("យើងខ្ញុំសូមថ្លែងអំ", khmer));
//            document.add(new Paragraph("បន្ថែមនេះនឹងមានសុពលភាពចាប់ពី  ថ្អែទី ២០ ខែ កញ្ញា ឆ្នាំ ២០២៣ តទៅ។ "
//                    + "ក្រុមហ៊ុនមិនតម្រូវឲ្យលោកអ្នកធ្វើអ្វីបន្ថែមឡើយ ហើយបុព្វលាភរ៉ាប់រងរបស់", khmer));
       }
        LayoutProcessor.disable();
    }
}
