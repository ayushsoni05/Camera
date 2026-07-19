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
        
        // Map simplified style choices to high-quality DiceBear parameters
        String hairStyle = "short05";
        if (state.hairStyle.equalsIgnoreCase("bald")) {
            hairStyle = "bald";
        } else if (state.hairStyle.equalsIgnoreCase("short")) {
            hairStyle = "short01";
        } else if (state.hairStyle.equalsIgnoreCase("medium")) {
            hairStyle = "long05";
        } else if (state.hairStyle.equalsIgnoreCase("long")) {
            hairStyle = "long10";
        }

        String eyes = "default";
        String mouth = "smile";
        String features = "";
        
        if (state.expression.equalsIgnoreCase("cool")) {
            eyes = "default";
            mouth = "neutral";
            features = "glasses"; // Adds cool glasses
        } else if (state.expression.equalsIgnoreCase("wink")) {
            eyes = "wink";
            mouth = "smile";
        } else if (state.expression.equalsIgnoreCase("surprised")) {
            eyes = "surprised";
            mouth = "open";
        }

        // Return a fully configured premium DiceBear Adventurer avatar request
        return "https://api.dicebear.com/7.x/adventurer/" + format + "?seed=snaptake" +
               "&skinColor=" + skin +
               "&hair=" + hairStyle +
               "&hairColor=" + hairColor +
               "&clothingColor=" + outfit +
               "&eyes=" + eyes +
               "&mouth=" + mouth +
               (features.isEmpty() ? "" : "&features=" + features);
    }
}
