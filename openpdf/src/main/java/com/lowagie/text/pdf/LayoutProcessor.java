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
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
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

    private static final Map<BaseFont, LazyFont> fopFontMap = new ConcurrentHashMap<>();

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
        LazyFont fopFont = null;
        try {
            fopFont = fopFontMap.get(baseFont);
            if (fopFont == null) {
                inputStream = getFontInputStream(filename);
                FopResourceResolver resourceResolver = new FopResourceResolver(inputStream);
                InternalResourceResolver internalResolver =
                        ResourceResolverFactory.createInternalResourceResolver(new URI(filename), resourceResolver);

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
                boolean useSVG = false;

                EmbedFontInfo fontInfo = new EmbedFontInfo(fontUris, kerning, advanced, fontTriplets, subFontName,
                        encodingMode, embeddingMode, simulateStyle, embedAsType1, useSVG);
                boolean useComplexScripts = true;
                fopFont = new LazyFont(fontInfo, internalResolver, useComplexScripts);

                if (fopFont != null) {
                    if (!globalTextAttributes.isEmpty()) {
                        // TODO: Apply globalTextAttributes
                        // Kerning, ligatures
                        // awtFont = awtFont.deriveFont(LayoutProcessor.globalTextAttributes);
                    }
                    fopFontMap.put(baseFont, fopFont);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Font creation failed for %s.", filename), e);
        } finally {
            // don't close inputStream, it is read later from LazyFont
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
    public static SimpleGlyphVector computeGlyphVector(BaseFont baseFont, float fontSize, String text) {
        SimpleGlyphVector glyphVector = null;
//        if(useFOP) {
        glyphVector = awtComputeGlyphVector(baseFont, fontSize, text);
//        } else {
        SimpleGlyphVector glyphVector2 = fopComputeGlyphVector(baseFont, fontSize, text);
//        }
        return glyphVector;
    }

    /**
     * Computes glyph positioning
     *
     * @param baseFont OpenPdf base font
     * @param text     input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    public static SimpleGlyphVector fopComputeGlyphVector(BaseFont baseFont, float fontSize, String text) {
        final char[] chars = text.toCharArray();

        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, true);
        // specify fractional metrics to compute accurate positions

        int localFlags = LayoutProcessor.flags;
        if (localFlags == DEFAULT_FLAGS) {
            AttributedString as = new AttributedString(text);
            Bidi bidi = new Bidi(as.getIterator());
            localFlags = bidi.isLeftToRight() ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT : java.awt.Font.LAYOUT_RIGHT_TO_LEFT;
        }
        final LazyFont fopFont = LayoutProcessor.fopFontMap.get(baseFont);
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

        final CharSequence substitutedGlyphs = fopFont.performSubstitution(text, script, language, associations, false);

        // 4. compute glyph position adjustments on (substituted) characters.
        final int[][] adjustments = fopFont.performsPositioning() ?
                fopFont.performPositioning(substitutedGlyphs, script, language) : null;

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
            widths[i] = fopFont.getWidth(cidSubsetGlyphIndices[i], (int) fontSize);
            if (widths[i] < 0) {
                widths[i] = 0;
            }
            if (adjustments != null) {
                widths[i] += adjustments[cpi][GlyphPositioningTable.Value.IDX_X_ADVANCE];
            }
            ipd = ipd.plus(widths[i]);
        }
/*
        public static final int IDX_X_PLACEMENT = 0;
        public static final int IDX_Y_PLACEMENT = 1;
        public static final int IDX_X_ADVANCE = 2;
        public static final int IDX_Y_ADVANCE = 3;
*/
        System.out.printf("fopComputeGlyphVector 2  dx dy dax day w  -- reordered \n");
        int[] glyphIndices =new int[reorderedGlyphs.length()];
        for (int i = 0; i < reorderedGlyphs.length(); i++) {
            char ci = reorderedGlyphs.charAt(i);
            System.out.printf("fopComputeGlyphVector i=%d c=%h",
                    i, ci);


            glyphIndices[i]  = ((MultiByteFont) fopFont.getRealFont()).findGlyphIndex((int) ci); // XXX Cast möglich?
            System.out.printf(" glyph=%d", glyphIndices[i]);
            //public int mapCodePoint(int cp) // XXX

            if (adjustments != null) {
                System.out.printf(" xp=%d yp=%d xa=%d ya=%d",
                        adjustments[i][Value.IDX_X_PLACEMENT],
                        adjustments[i][Value.IDX_Y_PLACEMENT],
                        adjustments[i][Value.IDX_X_ADVANCE],
                        adjustments[i][Value.IDX_Y_ADVANCE]);
            } else {
                System.out.printf(" adjustments null");
            }
            System.out.printf(" w=%d\n", widths[i]);
        }
        SimpleGlyphVector simpleFopGlyphVector = new SimpleFopGlyphVector(adjustments, reorderedGlyphs,
                cidSubsetGlyphIndices, glyphIndices, widths);

        return simpleFopGlyphVector;
    }

    interface SimpleGlyphVector {

        boolean hasAdjustments();

        Adjustments getAdjustments(int i);

        int[] getGlyphCodes();

    }
    static class Adjustments {
        private final double positionX;
        private final double positionY;
        private final double advanceX;
        private final double advanceY;
        public Adjustments(double positionX, double positionY, double advanceX, double advanceY) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.advanceX = advanceX;
            this.advanceY = advanceY;
        }
        double getPositionX() {
            return positionX;
        }
        double getPositionY() {
            return positionY;
        }
        double getAdvanceX() {
            return  advanceX;
        }
        double getAdvanceY() {
            return advanceY;
        }
    }

    static class SimpleAwtGlyphVector implements SimpleGlyphVector {
        private final GlyphVector awtGlyphVector;

        public SimpleAwtGlyphVector(GlyphVector awtGlyphVector) {
            this.awtGlyphVector = awtGlyphVector;
        }
        public Adjustments getAdjustments(int i) {
            return new Adjustments(awtGlyphVector.getGlyphPosition(i).getX(),
                    awtGlyphVector.getGlyphPosition(i).getY(),
                    awtGlyphVector.getGlyphMetrics(i).getAdvanceX(),
                    awtGlyphVector.getGlyphMetrics(i).getAdvanceX());
        }

        @Override
        public int[] getGlyphCodes() {
            return awtGlyphVector.getGlyphCodes(0, awtGlyphVector.getNumGlyphs() - 1, null);
        }

        @Override
        public boolean hasAdjustments() {
            return (GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS & awtGlyphVector.getLayoutFlags()) != 0;
        }
    }
    static class SimpleFopGlyphVector implements SimpleGlyphVector {
        private final int[][] adjustments;
        private final CharSequence glyphsAsChar;

        private final int[] cidSubsetGlyphIndices;
        private final int[] glyphCodes;
        private final int[] widths;

        public SimpleFopGlyphVector(int[][] adjustments, CharSequence glyphsAsChar, int[] cidSubsetGlyphIndices,
                int[] glyphIndices, int[] widths) {
            this.adjustments = adjustments;
            this.glyphsAsChar = glyphsAsChar;
            this.cidSubsetGlyphIndices = cidSubsetGlyphIndices;
            this.glyphCodes = glyphIndices;
            this.widths = widths;
        }
        public Adjustments getAdjustments(int i) {
            return new Adjustments(
                    adjustments[i][Value.IDX_X_PLACEMENT],
                    adjustments[i][Value.IDX_Y_PLACEMENT],
                    adjustments[i][Value.IDX_X_ADVANCE],
                    adjustments[i][Value.IDX_Y_ADVANCE]);
        }

        @Override
        public int[] getGlyphCodes() {
            return glyphCodes;
        }

        public CharSequence getCharacters() {
            return glyphsAsChar;
        }


        @Override
        public boolean hasAdjustments() {
            return adjustments != null;
        }
    }
    /**
     * Computes glyph positioning
     *
     * @param baseFont OpenPdf base font
     * @param text     input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    public static SimpleGlyphVector awtComputeGlyphVector(BaseFont baseFont, float fontSize, String text) {
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
                localFlags = runDirection == TextAttribute.RUN_DIRECTION_LTR ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT :
                        java.awt.Font.LAYOUT_RIGHT_TO_LEFT;
            }
        }
        return new SimpleAwtGlyphVector(awtFont.layoutGlyphVector(fontRenderContext, chars, 0, chars.length,
                localFlags));
    }


    /**
     * Checks if the glyphVector contains adjustments that make advanced layout necessary
     *
     * @param glyphVector glyph vector containing the positions
     * @return true, if the glyphVector contains adjustments
     */
    private static boolean hasAdjustments(GlyphVector glyphVector) {
        boolean retVal = false;
        float lastX = 0f;
        float lastY = 0f;

        for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
            Point2D p = glyphVector.getGlyphPosition(i);
            float dx = (float) p.getX() - lastX;
            float dy = (float) p.getY() - lastY;

            float ax = (i == 0) ? 0.0f : glyphVector.getGlyphMetrics(i - 1).getAdvanceX();
            float ay = (i == 0) ? 0.0f : glyphVector.getGlyphMetrics(i - 1).getAdvanceY();

            if (dx != ax || dy != ay) {
                retVal = true;
                break;
            }
            lastX = (float) p.getX();
            lastY = (float) p.getY();
        }
        return retVal;
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
        SimpleGlyphVector glyphVector = computeGlyphVector(baseFont, fontSize, text);

        System.out.print("chars: ");
        for (char c: text.toCharArray()) {
            System.out.printf("%04x ", (int)c);
        }
        System.out.println();
        System.out.println("glyphVector.getNumGlyphs()="+glyphVector.getNumGlyphs());
        System.out.printf("glyphs: ");
        for (int g: glyphVector.getGlyphCodes(0, glyphVector.getNumGlyphs(), null)) {
            System.out.printf("%d ", g);
        }
        System.out.println();
        int[] charIndizes = glyphVector.getGlyphCharIndices(0, glyphVector.getNumGlyphs(), null);
        //for (int ci: charIndizes) {
        //     System.out.println("charIndizes="+ci);
        //}

        if (!hasAdjustments(glyphVector)) {
            cb.showText(glyphVector);
            Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
            float dx = (float) p.getX();
            float dy = (float) p.getY();
            cb.moveTextBasic(dx, -dy);
            return new Point2D.Double(-dx, dy);
        }
        float lastX = 0f;
        float lastY = 0f;

        for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
            Point2D p = glyphVector.getGlyphPosition(i);

            float dx = (float) p.getX() - lastX;
            float dy = (float) p.getY() - lastY;

            cb.moveTextBasic(dx, -dy);

            cb.showText(glyphVector, i, i + 1);

            lastX = (float) p.getX();
            lastY = (float) p.getY();
        }
        Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
        float dx = (float) p.getX() - lastX;
        float dy = (float) p.getY() - lastY;
        cb.moveTextBasic(dx, -dy);
        return new Point2D.Double(-p.getX(), p.getY());
    }

    public static void disable() {
        enabled = false;
        flags = DEFAULT_FLAGS;
        awtFontMap.clear();
        globalTextAttributes.clear();
        useFOP = false;
    }
}
