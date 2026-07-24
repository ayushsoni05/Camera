package com.example.camera;

public class AvatarSvgGenerator {
    
    public static String generateSvg(AvatarState state) {
        // Return SVG url to be loaded directly inside Leaflet Map WebView
        return generateUrl(state, "svg");
    }

    public static String generateUrl(AvatarState state, String format) {
        if (state == null) {
            state = new AvatarState();
        }
        
        // Strip '#' from hex colors for API compatibility
        String skin = state.skinColor.replace("#", "").toLowerCase();
        String hairColor = state.hairColor.replace("#", "").toLowerCase();
        String outfit = state.outfitColor.replace("#", "").toLowerCase();
        
        // Map hair styles
        String top = state.hairStyle;
        if (top.equalsIgnoreCase("short")) {
            top = "shortHair";
        } else if (top.equalsIgnoreCase("medium")) {
            top = "longHairCurly";
        } else if (top.equalsIgnoreCase("long")) {
            top = "longHairStraight";
        } else if (top.equalsIgnoreCase("bald")) {
            top = "noHair";
        }

        // Map expressions/eyes
        String eyes = state.expression;
        if (eyes.equalsIgnoreCase("happy")) {
            eyes = "happy";
        } else if (eyes.equalsIgnoreCase("wink")) {
            eyes = "wink";
        } else if (eyes.equalsIgnoreCase("surprised")) {
            eyes = "surprised";
        } else if (eyes.equalsIgnoreCase("sleepy")) {
            eyes = "squint";
        } else if (eyes.equalsIgnoreCase("tired")) {
            eyes = "eyeRoll";
        } else if (eyes.equalsIgnoreCase("cool")) {
            eyes = "winkWacky";
        }

        // Return a fully configured premium DiceBear Avataaars vector request
        return "https://api.dicebear.com/7.x/avataaars/" + format + "?seed=snaptake" +
               "&skinColor=" + skin +
               "&top=" + top +
               "&topColor=" + hairColor +
               "&clothingColor=" + outfit +
               "&eyes=" + eyes +
               "&eyebrows=" + state.eyebrowsStyle +
               "&mouth=" + state.mouthStyle +
               "&accessories=" + state.accessories +
               "&facialHair=" + state.facialHair;
    }
}
