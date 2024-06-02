/*
 * LayoutProcessor.java
 *
 * Copyright 2020-2024 Volker Kunert.
 *
 * The contents of this file are subject to the Mozilla Public License Version 1.1
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the License.
 *
 *
 * Contributor(s): all the names of the contributors are added in the source code
 * where applicable.
 *
 * Alternatively, the contents of this file may be used under the terms of the
 * LGPL license (the "GNU LIBRARY GENERAL PUBLIC LICENSE"), in which case the
 * provisions of LGPL are applicable instead of those above.  If you wish to
 * allow use of your version of this file only under the terms of the LGPL
 * License and not to allow others to use your version of this file under
 * the MPL, indicate your decision by deleting the provisions above and
 * replace them with the notice and other provisions required by the LGPL.
 * If you do not delete the provisions above, a recipient may use your version
 * of this file under either the MPL or the GNU LIBRARY GENERAL PUBLIC LICENSE.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the MPL as stated above or under the terms of the GNU
 * Library General Public License as published by the Free Software Foundation;
 * either version 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library general Public License for more
 * details.
 *
 * If you didn't download this code from the following link, you should check if
 * you aren't using an obsolete version:
 * https://github.com/LibrePDF/OpenPDF
 */


package com.lowagie.text.pdf;

import com.lowagie.text.FontFactory;
import com.lowagie.text.error_messages.MessageLocalization;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.text.AttributedString;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fop.apps.io.InternalResourceResolver;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.fop.complexscripts.fonts.GlyphPositioningTable;
import org.apache.fop.complexscripts.fonts.GlyphPositioningTable.Value;
import org.apache.fop.complexscripts.util.CharScript;
import org.apache.fop.complexscripts.util.GlyphSequence;
import org.apache.fop.fonts.EmbedFontInfo;
import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.EncodingMode;
import org.apache.fop.fonts.FontTriplet;
import org.apache.fop.fonts.FontUris;
import org.apache.fop.fonts.LazyFont;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.traits.MinOptMax;
import org.apache.fop.util.CharUtilities;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

/**
 * Provides glyph layout e.g. for accented Latin letters.
 */
public class LayoutProcessor {

    private static final int DEFAULT_FLAGS = -1;
    private static final Map<BaseFont, java.awt.Font> awtFontMap = new ConcurrentHashMap<>();

    private static final Map<BaseFont, MultiByteFont> fopFontMap = new ConcurrentHashMap<>();

    private static final Map<TextAttribute, Object> globalTextAttributes = new ConcurrentHashMap<>();

    // Static variables can only be set once
    private static boolean enabled = false;
    private static int flags = DEFAULT_FLAGS;

    private static boolean useFOP = false;

    private LayoutProcessor() {
        throw new UnsupportedOperationException("static class");
    }

    /**
     * Enables the processor.
     * <p>
     * Kerning and ligatures are switched off. This method can only be called once.
     */
    public static void enableFop() {
        useFOP = true;
        enable();
    }


    /**
     * Enables the processor.
     * <p>
     * Kerning and ligatures are switched off. This method can only be called once.
     */
    public static void enable() {
        if (enabled) {
            throw new UnsupportedOperationException("LayoutProcessor is already enabled");
        }
        enabled = true;
    }

    /**
     * Enables the processor with the provided flags.
     * <p>
     * Kerning and ligatures are switched off. This method can only be called once.
     *
     * @param flags see java.awt.Font.layoutGlyphVector
     */
    public static void enable(int flags) {
        enable();
        LayoutProcessor.flags = flags;
    }

    /**
     * Enables the processor.
     * <p>
     * Kerning and ligatures are switched on. This method can only be called once.
     */
    public static void enableKernLiga() {
        enableKernLiga(DEFAULT_FLAGS);
    }

    /**
     * Enables the processor with the provided flags.
     * <p>
     * Kerning and ligatures are switched on. This method can only be called once.
     *
     * @param flags see java.awt.Font.layoutGlyphVector
     */
    public static void enableKernLiga(int flags) {
        if (enabled) {
            throw new UnsupportedOperationException("LayoutProcessor is already enabled");
        }
        setKerning();
        setLigatures();
        enable();
        LayoutProcessor.flags = flags;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Set kerning
     *
     * @see <a href="https://docs.oracle.com/javase/tutorial/2d/text/textattributes.html">
     * Oracle: The Java™ Tutorials, Using Text Attributes to Style Text</a>
     */
    public static void setKerning() {
        LayoutProcessor.globalTextAttributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
    }

    /**
     * Set kerning for one font
     *
     * @param font The font for which kerning is to be turned on
     * @see <a href="https://docs.oracle.com/javase/tutorial/2d/text/textattributes.html">
     * Oracle: The Java™ Tutorials, Using Text Attributes to Style Text</a>
     */
    public static void setKerning(com.lowagie.text.Font font) {
        Map<TextAttribute, Object> textAttributes = new HashMap<>();
        textAttributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        setTextAttributes(font, textAttributes);
    }

    /**
     * Add ligatures
     */
    public static void setLigatures() {
        LayoutProcessor.globalTextAttributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
    }

    /**
     * Set ligatures for one font
     *
     * @param font The font for which ligatures are to be turned on
     */
    public static void setLigatures(com.lowagie.text.Font font) {
        Map<TextAttribute, Object> textAttributes = new HashMap<>();
        textAttributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        setTextAttributes(font, textAttributes);
    }

    /**
     * Set run direction for one font to RTL
     *
     * @param font The font for which the run direction is set
     */
    public static void setRunDirectionRtl(com.lowagie.text.Font font) {
        setRunDirection(font, TextAttribute.RUN_DIRECTION_RTL);
    }

    /**
     * Set run direction for one font to LTR
     *
     * @param font The font for which the run direction is set
     */
    public static void setRunDirectionLtr(com.lowagie.text.Font font) {
        setRunDirection(font, TextAttribute.RUN_DIRECTION_LTR);
    }

    /**
     * Set run direction for one font
     *
     * @param font The font for which the run direction is set
     */
    private static void setRunDirection(com.lowagie.text.Font font, Boolean runDirection) {
        Map<TextAttribute, Object> textAttributes = new HashMap<>();
        textAttributes.put(TextAttribute.RUN_DIRECTION, runDirection);
        setTextAttributes(font, textAttributes);
    }

    /**
     * Set text attributes to font The attributes are used only for glyph layout, and don't change the visual appearance
     * of the font
     *
     * @param font           The font for which kerning is to be turned on
     * @param textAttributes Map of text attributes to be set
     * @see <a href="https://docs.oracle.com/javase/tutorial/2d/text/textattributes.html" >Oracle: The Java™ Tutorials,
     * Using Text Attributes to Style Text</a>
     */
    private static void setTextAttributes(com.lowagie.text.Font font, Map<TextAttribute, Object> textAttributes) {
        BaseFont baseFont = font.getBaseFont();
        java.awt.Font awtFont = awtFontMap.get(baseFont);
        if (awtFont != null) {
            awtFont = awtFont.deriveFont(textAttributes);
            awtFontMap.put(baseFont, awtFont);
        }
    }

    public static int getFlags() {
        return flags;
    }

    public static boolean isSet(int queryFlags) {
        return flags != DEFAULT_FLAGS && (flags & queryFlags) == queryFlags;
    }

    public static boolean supportsFont(BaseFont baseFont) {
        return enabled && (awtFontMap.get(baseFont) != null);
    }

    /**
     * Loads the AWT font needed for layout
     *
     * @param baseFont OpenPdf base font
     * @param filename of the font file
     * @throws RuntimeException if font can not be loaded
     */
    public static void loadFont(BaseFont baseFont, String filename) {
        if (!enabled) {
            return;
        }
//        if (!useFOP) {
        loadAwtFont(baseFont, filename);
//        } else {
        loadFopFont(baseFont, filename);
//        }
    }

    /**
     * Loads the AWT font needed for layout
     *
     * @param baseFont OpenPdf base font
     * @param filename of the font file
     * @throws RuntimeException if font can not be loaded
     */
    private static void loadAwtFont(BaseFont baseFont, String filename) {
        java.awt.Font awtFont;
        InputStream inputStream = null;
        try {
            awtFont = awtFontMap.get(baseFont);
            if (awtFont == null) {
                inputStream = getFontInputStream(filename);
                awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
                if (awtFont != null) {
                    if (!globalTextAttributes.isEmpty()) {
                        awtFont = awtFont.deriveFont(LayoutProcessor.globalTextAttributes);
                    }
                    awtFontMap.put(baseFont, awtFont);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Font creation failed for %s.", filename), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Loads the FOP font needed for layout
     *
     * @param baseFont OpenPdf base font
     * @param filename of the font file
     * @throws RuntimeException if font can not be loaded
     */
    private static void loadFopFont(BaseFont baseFont, String filename) {
        // TODO:
        InputStream inputStream = null;
        MultiByteFont fopFont = null;
        try {
            fopFont = fopFontMap.get(baseFont);
            if (fopFont == null) {
                inputStream = getFontInputStream(filename);
                FopResourceResolver resourceResolver = new FopResourceResolver(inputStream);
                InternalResourceResolver internalResolver = ResourceResolverFactory.createInternalResourceResolver(
                        new URI(filename), resourceResolver);

                FontUris fontUris = new FontUris(new URI(baseFont.getPostscriptFontName()), null);
                boolean kerning = true;
                boolean advanced = true;
                FontTriplet fontTriplet = new FontTriplet();
                List<FontTriplet> fontTriplets = new ArrayList<>();
                fontTriplets.add(fontTriplet);

                String subFontName = baseFont.getPostscriptFontName();
                EncodingMode encodingMode = EncodingMode.AUTO;
                EmbeddingMode embeddingMode = EmbeddingMode.AUTO;
                boolean simulateStyle = false;
                boolean embedAsType1 = false;
                boolean useSVG = true;

                EmbedFontInfo fontInfo = new EmbedFontInfo(fontUris, kerning, advanced, fontTriplets, subFontName,
                        encodingMode, embeddingMode, simulateStyle, embedAsType1, useSVG);
                boolean useComplexScripts = true;
                LazyFont lazyFont = new LazyFont(fontInfo, internalResolver, useComplexScripts);

                if (lazyFont != null) {
                    if (!globalTextAttributes.isEmpty()) {
                        // TODO: Apply globalTextAttributes
                        // Kerning, ligatures
                    }
                    fopFontMap.put(baseFont, (MultiByteFont)(lazyFont.getRealFont()));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Font creation failed for %s.", filename), e);
        }
    }

    private static InputStream getFontInputStream(String filename) throws IOException {
        // getting the inputStream is adapted from com.lowagie.text.pdf.RandomAccessFileOrArray
        InputStream inputStream;
        File file = new File(filename);
        if (!file.exists() && FontFactory.isRegistered(filename)) {
            filename = (String) FontFactory.getFontImp().getFontPath(filename);
            file = new File(filename);
        }
        if (file.canRead()) {
            inputStream = Files.newInputStream(file.toPath());
        } else if (filename.startsWith("file:/") || filename.startsWith("http://") || filename.startsWith("https://")
                || filename.startsWith("jar:") || filename.startsWith("wsjar:")) {
            inputStream = new URL(filename).openStream();
        } else if ("-".equals(filename)) {
            inputStream = System.in;
        } else {
            inputStream = BaseFont.getResourceStream(filename);
        }
        if (inputStream == null) {
            throw new IOException(MessageLocalization.getComposedMessage("1.not.found.as.file.or.resource", filename));
        }
        return inputStream;
    }

    /**
     * Computes glyph positioning
     *
     * @param baseFont OpenPdf base font
     * @param text     input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    public static LPGlyphVector computeGlyphVector(BaseFont baseFont, float fontSize, String text) {
        LPGlyphVector glyphVector = null;
//        if(useFOP) {
        glyphVector = awtComputeGlyphVector(baseFont, fontSize, text);
//        } else {
        LPGlyphVector glyphVectorFop = fopComputeGlyphVector(baseFont, (int)(fontSize*1000), text);
//        }
//        return glyphVectorFop;
        return glyphVectorFop;
        // XXX Test mit FOP Text: "Test" Font NotoSerif-Regular hb-shape
    }

    /**
     * Computes glyph positioning
     *
     * @param baseFont OpenPdf base font
     * @param text     input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    public static LPGlyphVector fopComputeGlyphVector(BaseFont baseFont, int fontSize1000, String text) {
        final char[] chars = text.toCharArray();

        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, true);
        // specify fractional metrics to compute accurate positions

        int localFlags = LayoutProcessor.flags;
        if (localFlags == DEFAULT_FLAGS) {
            AttributedString as = new AttributedString(text);
            Bidi bidi = new Bidi(as.getIterator());
            localFlags = bidi.isLeftToRight() ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT : java.awt.Font.LAYOUT_RIGHT_TO_LEFT;
        }
        final MultiByteFont fopFont = LayoutProcessor.fopFontMap.get(baseFont);

/*
Adapted from org.apache.fop.fonts.GlyphMapping.doWordMapping;
The Apache™ FOP Project
https://www.apache.org/licenses/LICENSE-2.0
 */
        //   int nLS = 0; // # of letter spaces
        String script = null; //text.getScript();
        String language = null; //text.getLanguage();

        // 2. if script is not specified (by FO property) or it is specified as 'auto',
        // then compute dominant script.
        if ((script == null) || "auto".equals(script)) {
            script = CharScript.scriptTagFromCode(CharScript.dominantScript(text));
        }
        if ((language == null) || "none".equals(language)) {
            language = "dflt";
        }

        // 3. perform mapping of chars to glyphs ... to glyphs ... to chars, retaining
        // associations if requested.
        final List associations = new ArrayList();

        // This is a workaround to read the ligature from the font even if the script
        // does not match the one defined for the table.
        // More info here: https://issues.apache.org/jira/browse/FOP-2638
        // zyyy == SCRIPT_UNDEFINED
        if ("zyyy".equals(script) || "auto".equals(script)) {
            script = "*";
        }

        final CharSequence substitutedGlyphs = fopFont.performSubstitution(text, script, language, associations, true);

        // 4. compute glyph position adjustments on (substituted) characters.
        final int[][] adjustments =
                fopFont.performsPositioning() ? fopFont.performPositioning(substitutedGlyphs, script, language,
                        (int)fontSize1000) :
                        null;
        // XXX MultiByteFont 465: getUnscaledWidth(gs) statt this.width?
        // Wie sieht das mit demselben Textbeispiel in FOP aus?

/*
            if (useKerningAdjustments(fopFont, script, language)) {
                // handle standard (non-GPOS) kerning adjustments
                gpa = getKerningAdjustments(mcs, font, gpa);
            }
*/
        // 5. reorder combining marks so that they precede (within the mapped char sequence) the
        // base to which they are applied; N.B. position adjustments are reordered in place.
        final CharSequence reorderedGlyphs = fopFont.reorderCombiningMarks(substitutedGlyphs, adjustments, script,
                language, associations);

        // 6. compute word ipd based on final position adjustments.
        MinOptMax ipd = MinOptMax.ZERO;

        final int[] widths = new int[reorderedGlyphs.length()];
        final int[] cidSubsetGlyphIndices = new int[reorderedGlyphs.length()];
        // The gpa array is sized by code point count
        for (int i = 0, cpi = 0, n = reorderedGlyphs.length(); i < n; i++, cpi++) {
            int c = reorderedGlyphs.charAt(i);
            cidSubsetGlyphIndices[i] = fopFont.mapChar((char) c);

            if (CharUtilities.containsSurrogatePairAt(reorderedGlyphs, i)) {
                c = Character.toCodePoint((char) c, reorderedGlyphs.charAt(++i));
                fopFont.mapChar((char) c);
                // XXX
            }
            // XXX Compute positions from adjustments, see fop/PDFPainter/drawTextWithDP
            // XXX Or layout with adjustments, not positions ... siehe aktuellen Branch ...
            widths[i] = fopFont.getWidth(cidSubsetGlyphIndices[i], (int) fontSize1000); // XXX Ist das hier die richtige
            // Breite
            if (widths[i] < 0) {
                widths[i] = 0;
            }
            if (adjustments != null) {
                widths[i] += adjustments[cpi][GlyphPositioningTable.Value.IDX_X_ADVANCE];
            }
            ipd = ipd.plus(widths[i]);

        }
        int total_width = 0;
        for (int w: widths) {
            total_width += w;
        }

/*
        public static final int IDX_X_PLACEMENT = 0;
        public static final int IDX_Y_PLACEMENT = 1;
        public static final int IDX_X_ADVANCE = 2;
        public static final int IDX_Y_ADVANCE = 3;
*/
        System.out.print("fopComputeGlyphVector 2  dx dy dax day w  -- reordered \n");
        int[] glyphIndices = new int[reorderedGlyphs.length()];
        for (int i = 0; i < reorderedGlyphs.length(); i++) {
            char ci = reorderedGlyphs.charAt(i);
            System.out.printf("fopComputeGlyphVector i=%d c=%h", i, ci);

            glyphIndices[i] = fopFont.findGlyphIndex(ci); // XXX Cast möglich?
            System.out.printf(" glyph=%d", glyphIndices[i]);
            //public int mapCodePoint(int cp) // XXX

            if (adjustments != null) {
                System.out.printf(" xp=%d yp=%d xa=%d ya=%d", adjustments[i][Value.IDX_X_PLACEMENT],
                        adjustments[i][Value.IDX_Y_PLACEMENT], adjustments[i][Value.IDX_X_ADVANCE],
                        adjustments[i][Value.IDX_Y_ADVANCE]);
            } else {
                System.out.print(" adjustments null");
            }
            System.out.printf(" w=%d\n", widths[i]);
        }
        LPGlyphVector fopGlyphVector = new FopGlyphVector(adjustments, reorderedGlyphs, cidSubsetGlyphIndices,
                glyphIndices, associations, widths, total_width);

        return fopGlyphVector;
    }

    static abstract class  LPGlyphVector extends java.awt.font.GlyphVector {
        abstract public double[][] getAdjustments();
    }
    static class AwtGlyphVector extends LPGlyphVector {

        private final GlyphVector glyphVector;

        public AwtGlyphVector(GlyphVector glyphVector) {
            this.glyphVector = glyphVector;
        }

        @Override
        public Font getFont() {
            return glyphVector.getFont();
        }

        @Override
        public FontRenderContext getFontRenderContext() {
            return glyphVector.getFontRenderContext();
        }

        @Override
        public void performDefaultLayout() {
            glyphVector.performDefaultLayout();
        }

        @Override
        public int getNumGlyphs() {
            return glyphVector.getNumGlyphs();
        }

        @Override
        public int getGlyphCode(int glyphIndex) {
            return glyphVector.getGlyphCode(glyphIndex);
        }

        @Override
        public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
            return glyphVector.getGlyphCodes(beginGlyphIndex, numEntries, codeReturn);
        }

        @Override
        public Rectangle2D getLogicalBounds() {
            return glyphVector.getLogicalBounds();
        }

        @Override
        public Rectangle2D getVisualBounds() {
            return glyphVector.getVisualBounds();
        }

        @Override
        public Shape getOutline() {
            return glyphVector.getOutline();
        }

        @Override
        public Shape getOutline(float x, float y) {
            return glyphVector.getOutline(x, y);
        }

        @Override
        public Shape getGlyphOutline(int glyphIndex) {
            return glyphVector.getGlyphOutline(glyphIndex);
        }

        @Override
        public Point2D getGlyphPosition(int glyphIndex) {
            return glyphVector.getGlyphPosition(glyphIndex);
        }

        @Override
        public void setGlyphPosition(int glyphIndex, Point2D newPos) {
            glyphVector.setGlyphPosition(glyphIndex, newPos);
        }

        @Override
        public AffineTransform getGlyphTransform(int glyphIndex) {
            return glyphVector.getGlyphTransform(glyphIndex);
        }

        @Override
        public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
            glyphVector.setGlyphTransform(glyphIndex, newTX);
        }

        @Override
        public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
            return glyphVector.getGlyphPositions(beginGlyphIndex, numEntries, positionReturn);
        }

        @Override
        public Shape getGlyphLogicalBounds(int glyphIndex) {
            return glyphVector.getGlyphLogicalBounds(glyphIndex);
        }

        @Override
        public Shape getGlyphVisualBounds(int glyphIndex) {
            return glyphVector.getGlyphVisualBounds(glyphIndex);
        }

        @Override
        public GlyphMetrics getGlyphMetrics(int glyphIndex) {
            return glyphVector.getGlyphMetrics(glyphIndex);
        }

        @Override
        public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
            return glyphVector.getGlyphJustificationInfo(glyphIndex);
        }

        @Override
        public boolean equals(GlyphVector set) {
            return glyphVector.equals(set);
        }

        @Override
        public int getGlyphCharIndex(int glyphIndex) {
            return glyphVector.getGlyphCharIndex(glyphIndex);
        }

        @Override
        public int[] getGlyphCharIndices(int beginGlyphIndex, int numEntries, int[] codeReturn) {
            return glyphVector.getGlyphCharIndices(beginGlyphIndex, numEntries, codeReturn);
        }

        @Override
        public Rectangle getPixelBounds(FontRenderContext renderFRC, float x, float y) {
            return glyphVector.getPixelBounds(renderFRC, x, y);
        }

        @Override
        public Shape getGlyphOutline(int glyphIndex, float x, float y) {
            return glyphVector.getGlyphOutline(glyphIndex, x, y);
        }

        @Override
        public int getLayoutFlags() {
            return glyphVector.getLayoutFlags();
        }

        @Override
        public Rectangle getGlyphPixelBounds(int index, FontRenderContext renderFRC, float x, float y) {
            return glyphVector.getGlyphPixelBounds(index, renderFRC, x, y);
        }

        public double[][] getAdjustments() {
            /*
            public static final int IDX_X_PLACEMENT = 0;
            public static final int IDX_Y_PLACEMENT = 1;
            public static final int IDX_X_ADVANCE = 2;
            public static final int IDX_Y_ADVANCE = 3;
            */

            double[][] adjustments = new double[glyphVector.getNumGlyphs()][4];

            double lastX = 0.0;
            double lastY = 0.0;

            for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
                Point2D p = glyphVector.getGlyphPosition(i);

                adjustments[i][Value.IDX_X_PLACEMENT] = p.getX() - lastX;
                adjustments[i][Value.IDX_Y_PLACEMENT] = p.getY() - lastY;

                lastX = p.getX();
                lastY = p.getY();
            }
            Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
            adjustments[glyphVector.getNumGlyphs()-1][Value.IDX_X_ADVANCE] = p.getX() - lastX;
            adjustments[glyphVector.getNumGlyphs()-1][Value.IDX_Y_ADVANCE] = p.getY() - lastY;

            return adjustments;
        }
    }

    /**
     * Computes glyph positioning
     *
     * @param baseFont OpenPdf base font
     * @param text     input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    public static LPGlyphVector awtComputeGlyphVector(BaseFont baseFont, float fontSize, String text) {
        char[] chars = text.toCharArray();

        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, true);
        // specify fractional metrics to compute accurate positions

        int localFlags = LayoutProcessor.flags;
        if (localFlags == DEFAULT_FLAGS) {
            AttributedString as = new AttributedString(text);
            Bidi bidi = new Bidi(as.getIterator());
            localFlags = bidi.isLeftToRight() ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT : java.awt.Font.LAYOUT_RIGHT_TO_LEFT;
        }
        java.awt.Font awtFont = LayoutProcessor.awtFontMap.get(baseFont).deriveFont(fontSize);
        Map<TextAttribute, ?> textAttributes = awtFont.getAttributes();
        if (textAttributes != null) {
            Object runDirection = textAttributes.get(TextAttribute.RUN_DIRECTION);
            if (runDirection != null) {
                localFlags = runDirection == TextAttribute.RUN_DIRECTION_LTR ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT
                        : java.awt.Font.LAYOUT_RIGHT_TO_LEFT;
            }
        }
        return new AwtGlyphVector(awtFont.layoutGlyphVector(fontRenderContext, chars, 0, chars.length, localFlags));
    }

    /**
     * Checks if the glyphVector contains adjustments that make advanced layout necessary
     *
     * @param glyphVector glyph vector containing the positions
     * @return true, if the glyphVector contains adjustments
     */
    private static boolean hasAdjustments(GlyphVector glyphVector) {
        return (glyphVector.getLayoutFlags() & GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS) != 0;
    }

    /**
     * Shows a text using glyph positioning (if needed)
     *
     * @param cb       object containing the content of the page
     * @param baseFont base font to use
     * @param fontSize font size to apply
     * @param text     text to show
     * @return layout position correction to correct the start of the next line
     */
    public static Point2D showText(PdfContentByte cb, BaseFont baseFont, float fontSize, String text) {
        LPGlyphVector glyphVector = computeGlyphVector(baseFont, fontSize, text);

        System.out.print("chars: ");
        for (char c : text.toCharArray()) {
            System.out.printf("%04x ", (int) c);
        }
        System.out.println();
        System.out.println("glyphVector.getNumGlyphs()=" + glyphVector.getNumGlyphs());
        System.out.print("glyphs: ");
        for (int g : glyphVector.getGlyphCodes(0, glyphVector.getNumGlyphs(), null)) {
            System.out.printf("%d ", g);
        }
        System.out.println();
        int[] charIndizes = glyphVector.getGlyphCharIndices(0, glyphVector.getNumGlyphs(), null);
        //for (int ci: charIndizes) {
        //     System.out.println("charIndizes="+ci);
        //}

        if (!hasAdjustments(glyphVector)) {
            cb.showText(glyphVector);
            // XXX Wird die Breite noch richtig bei Ligaturen etc?
/*            Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
            float dx = (float) p.getX();
            float dy = (float) p.getY();
            cb.moveTextBasic(dx, -dy);
*/
            float dx = 0f;
            float dy = 0f;
            return new Point2D.Float(-dx, dy);
        }
        float lastX = 0f;
        float lastY = 0f;

        double[][] adjustments = glyphVector.getAdjustments();
        for (int i = 0; i < adjustments.length; i++) {

            double dx = (float) adjustments[i][Value.IDX_X_PLACEMENT] + (i>0 ? adjustments[i-1][Value.IDX_X_ADVANCE] :
                    0.0);
            double dy = adjustments[i][Value.IDX_Y_PLACEMENT] + (i>0 ? adjustments[i-1][Value.IDX_Y_ADVANCE] : 0.0);

            cb.moveTextBasic((float)dx, (float)-dy);
            cb.showText(glyphVector, i, i + 1);
        }
        Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
        double dx = adjustments[adjustments.length - 1][Value.IDX_X_ADVANCE];
        double dy = adjustments[adjustments.length - 1][Value.IDX_Y_ADVANCE];
        cb.moveTextBasic((float)dx, (float)-dy);
        return new Point2D.Double(-p.getX(), p.getY());
    }

    public static void disable() {
        enabled = false;
        flags = DEFAULT_FLAGS;
        awtFontMap.clear();
        globalTextAttributes.clear();
        useFOP = false;
    }

    private static class FopResourceResolver implements ResourceResolver {

        private final InputStream inputStream;

        public FopResourceResolver(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public Resource getResource(URI uri) throws IOException {
            return new Resource(inputStream);
        }

        @Override
        public OutputStream getOutputStream(URI uri) throws IOException {
            return null;
        }
    }

    static class FopGlyphVector extends LPGlyphVector {

        /**
         * A flag used with getLayoutFlags that indicates that this {@code GlyphVector} has per-glyph transforms.
         *
         * @since 1.4
         */
        public static final int FLAG_HAS_TRANSFORMS = 1;
        /**
         * A flag used with getLayoutFlags that indicates that this {@code GlyphVector} has position adjustments.  When
         * this is true, the glyph positions don't match the accumulated default advances of the glyphs (for example, if
         * kerning has been done).
         *
         * @since 1.4
         */
        public static final int FLAG_HAS_POSITION_ADJUSTMENTS = 2;
        /**
         * A flag used with getLayoutFlags that indicates that this {@code GlyphVector} has a right-to-left run
         * direction.  This refers to the glyph-to-char mapping and does not imply that the visual locations of the
         * glyphs are necessarily in this order, although generally they will be.
         *
         * @since 1.4
         */
        public static final int FLAG_RUN_RTL = 4;
        /**
         * A flag used with getLayoutFlags that indicates that this {@code GlyphVector} has a complex glyph-to-char
         * mapping (one that does not map glyphs to chars one-to-one in strictly ascending or descending order matching
         * the run direction).
         *
         * @since 1.4
         */
        public static final int FLAG_COMPLEX_GLYPHS = 8;
        private final int[][] adjustments;
        private final CharSequence glyphsAsChar;
        private final int[] cidSubsetGlyphIndices;
        private final int[] glyphCodes;
        private final List associations;
        private final int[] widths;
        private final int total_width;

        public FopGlyphVector(int[][] adjustments, CharSequence glyphsAsChar, int[] cidSubsetGlyphIndices,
                int[] glyphIndices, List associations, int[] widths, int total_width) {
            this.adjustments = adjustments;
            this.glyphsAsChar = glyphsAsChar;
            this.cidSubsetGlyphIndices = cidSubsetGlyphIndices;
            this.glyphCodes = glyphIndices;
            this.associations = associations;
            this.widths = widths;
            this.total_width = total_width;
        }

        public boolean hasAdjustments() {
            return adjustments != null;
        }

        @Override
        public Font getFont() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FontRenderContext getFontRenderContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void performDefaultLayout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNumGlyphs() {
            return glyphCodes.length;
        }

        @Override
        public int getGlyphCode(int glyphIndex) {
            return glyphCodes[glyphIndex];
        }

        public char getGlyphChar(int glyphIndex) {
            return glyphsAsChar.charAt(glyphIndex);
        }

        @Override
        public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
            int[] returnValue = codeReturn != null ? codeReturn : new int[numEntries];
            System.arraycopy(this.glyphCodes, beginGlyphIndex, returnValue, 0, numEntries);
            return returnValue;
        }

        @Override
        public Rectangle2D getLogicalBounds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle2D getVisualBounds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Shape getOutline() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Shape getOutline(float x, float y) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Shape getGlyphOutline(int glyphIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLayoutFlags() {
            // FLAG_HAS_TRANSFORMS
            // FLAG_HAS_POSITION_ADJUSTMENTS
            // FLAG_RUN_RTL
            // FLAG_COMPLEX_GLYPHS
            int flags = 0 | (adjustments != null ? FLAG_HAS_POSITION_ADJUSTMENTS : 0);
            return flags;

        }

        @Override
        public Point2D getGlyphPosition(int glyphIndex) {
            if (glyphIndex < glyphCodes.length) {
                Point2D p = new Point2D.Double(adjustments[glyphIndex][Value.IDX_X_PLACEMENT],
                        adjustments[glyphIndex][Value.IDX_Y_PLACEMENT]);
                return p;
            } else {
                return new Point2D.Double(total_width, 0);
            }
        }

        @Override
        public void setGlyphPosition(int glyphIndex, Point2D newPos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AffineTransform getGlyphTransform(int glyphIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
            return new float[0];
        }

        @Override
        public Shape getGlyphLogicalBounds(int glyphIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Shape getGlyphVisualBounds(int glyphIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GlyphMetrics getGlyphMetrics(int glyphIndex) {
            return null;
        }

        @Override
        public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(GlyphVector set) {
            return false;
        }

        public double[][] getAdjustments() {
            double[][] doubleAdjustments = new double[getNumGlyphs()][4];

            double lastX = 0.0;
            double lastY = 0.0;

            for (int i = 0; i < getNumGlyphs(); i++) {
                for(int j = 0; j < 4; j++) {
                    doubleAdjustments[i][j] = adjustments[i][j];
                }
            }
            return doubleAdjustments;
        }
    }
}
