package com.example.camera;

public class AvatarSvgGenerator {
    
    public static String generateSvg(AvatarState state) {
        if (state == null) {
            state = new AvatarState();
        }
        
        StringBuilder svg = new StringBuilder();
        // Construct clean SVG matching the Canvas layout
        svg.append("<svg viewBox=\\\"0 0 100 100\\\" width=\\\"30\\\" height=\\\"30\\\">");
        
        // 1. Body / Outfit
        svg.append("<ellipse cx=\\\"50\\\" cy=\\\"96\\\" rx=\\\"34\\\" ry=\\\"24\\\" fill=\\\"").append(state.outfitColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");
        svg.append("<path d=\\\"M35 68 Q50 80 65 68\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");

        // 2. Ears
        svg.append("<circle cx=\\\"24\\\" cy=\\\"48\\\" r=\\\"4.5\\\" fill=\\\"").append(state.skinColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");
        svg.append("<circle cx=\\\"76\\\" cy=\\\"48\\\" r=\\\"4.5\\\" fill=\\\"").append(state.skinColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");

        // 3. Head / Face
        svg.append("<circle cx=\\\"50\\\" cy=\\\"48\\\" r=\\\"25\\\" fill=\\\"").append(state.skinColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");

        // 4. Eyebrows
        svg.append("<path d=\\\"M33 38 Q39 35 45 38\\\" fill=\\\"none\\\" stroke=\\\"").append(state.hairColor).append("\\\" stroke-width=\\\"2\\\" stroke-linecap=\\\"round\\\" />");
        svg.append("<path d=\\\"M55 38 Q61 35 67 38\\\" fill=\\\"none\\\" stroke=\\\"").append(state.hairColor).append("\\\" stroke-width=\\\"2\\\" stroke-linecap=\\\"round\\\" />");

        // 5. Nose
        svg.append("<path d=\\\"M47 49 Q50 52 53 49\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" stroke-linecap=\\\"round\\\" />");

        // 6. Expression: Eyes & Mouth
        if (state.expression.equalsIgnoreCase("cool")) {
            svg.append("<rect x=\\\"30\\\" y=\\\"39\\\" width=\\\"17\\\" height=\\\"9\\\" rx=\\\"4\\\" ry=\\\"4\\\" fill=\\\"#111119\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
            svg.append("<rect x=\\\"53\\\" y=\\\"39\\\" width=\\\"17\\\" height=\\\"9\\\" rx=\\\"4\\\" ry=\\\"4\\\" fill=\\\"#111119\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
            svg.append("<line x1=\\\"47\\\" y1=\\\"42\\\" x2=\\\"53\\\" y2=\\\"42\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");
            svg.append("<path d=\\\"M44 59 Q51 63 58 59\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" stroke-linecap=\\\"round\\\" />");
        } else if (state.expression.equalsIgnoreCase("wink")) {
            svg.append("<circle cx=\\\"39\\\" cy=\\\"44\\\" r=\\\"3\\\" fill=\\\"#000000\\\" />");
            svg.append("<path d=\\\"M57 44 Q62 40 67 44\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" stroke-linecap=\\\"round\\\" />");
            svg.append("<path d=\\\"M41 57 Q50 66 59 57\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" stroke-linecap=\\\"round\\\" />");
        } else if (state.expression.equalsIgnoreCase("surprised")) {
            svg.append("<circle cx=\\\"39\\\" cy=\\\"44\\\" r=\\\"4.5\\\" fill=\\\"#FFFFFF\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");
            svg.append("<circle cx=\\\"61\\\" cy=\\\"44\\\" r=\\\"4.5\\\" fill=\\\"#FFFFFF\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" />");
            svg.append("<circle cx=\\\"39\\\" cy=\\\"44\\\" r=\\\"1.8\\\" fill=\\\"#000000\\\" />");
            svg.append("<circle cx=\\\"61\\\" cy=\\\"44\\\" r=\\\"1.8\\\" fill=\\\"#000000\\\" />");
            svg.append("<ellipse cx=\\\"50\\\" cy=\\\"60\\\" rx=\\\"4\\\" ry=\\\"5\\\" fill=\\\"#000000\\\" />");
        } else {
            // happy
            svg.append("<circle cx=\\\"39\\\" cy=\\\"44\\\" r=\\\"3\\\" fill=\\\"#000000\\\" />");
            svg.append("<circle cx=\\\"61\\\" cy=\\\"44\\\" r=\\\"3\\\" fill=\\\"#000000\\\" />");
            svg.append("<circle cx=\\\"38\\\" cy=\\\"43\\\" r=\\\"0.8\\\" fill=\\\"#FFFFFF\\\" />");
            svg.append("<circle cx=\\\"60\\\" cy=\\\"43\\\" r=\\\"0.8\\\" fill=\\\"#FFFFFF\\\" />");
            svg.append("<path d=\\\"M41 57 Q50 66 59 57\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2.5\\\" stroke-linecap=\\\"round\\\" />");
        }

        // 7. Hair
        if (!state.hairStyle.equalsIgnoreCase("bald")) {
            if (state.hairStyle.equalsIgnoreCase("short")) {
                svg.append("<path d=\\\"M25 35 A28 28 0 0 1 75 35 Z\\\" fill=\\\"").append(state.hairColor).append("\\\" />");
                svg.append("<path d=\\\"M23 44 L26 32 L38 30 L44 25 L50 31 L56 25 L62 30 L74 32 L77 44 L74 36 L66 24 L50 21 L34 24 L26 36 Z\\\" fill=\\\"").append(state.hairColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
            } else if (state.hairStyle.equalsIgnoreCase("medium")) {
                svg.append("<path d=\\\"M25 35 A28 28 0 0 1 75 35 Z\\\" fill=\\\"").append(state.hairColor).append("\\\" />");
                svg.append("<rect x=\\\"20\\\" y=\\\"32\\\" width=\\\"7\\\" height=\\\"24\\\" rx=\\\"4\\\" ry=\\\"4\\\" fill=\\\"").append(state.hairColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
                svg.append("<rect x=\\\"73\\\" y=\\\"32\\\" width=\\\"7\\\" height=\\\"24\\\" rx=\\\"4\\\" ry=\\\"4\\\" fill=\\\"").append(state.hairColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
                svg.append("<path d=\\\"M26 38 Q38 28 50 31 Q62 28 74 38\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
            } else if (state.hairStyle.equalsIgnoreCase("long")) {
                svg.append("<path d=\\\"M25 35 A28 28 0 0 1 75 35 Z\\\" fill=\\\"").append(state.hairColor).append("\\\" />");
                svg.append("<rect x=\\\"18\\\" y=\\\"30\\\" width=\\\"8\\\" height=\\\"45\\\" rx=\\\"6\\\" ry=\\\"6\\\" fill=\\\"").append(state.hairColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
                svg.append("<rect x=\\\"74\\\" y=\\\"30\\\" width=\\\"8\\\" height=\\\"45\\\" rx=\\\"6\\\" ry=\\\"6\\\" fill=\\\"").append(state.hairColor).append("\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
                svg.append("<path d=\\\"M25 34 Q38 26 50 34 Q62 26 75 34\\\" fill=\\\"none\\\" stroke=\\\"#000000\\\" stroke-width=\\\"2\\\" />");
            }
        }

        svg.append("</svg>");
        return svg.toString();
    }
}
