package com.example.camera;

public class AvatarState {
    public String skinColor = "#FFE0BD";
    public String hairStyle = "short";
    public String hairColor = "#090806";
    public String outfitColor = "#6366F1";
    public String expression = "happy";

    public String eyebrowsStyle = "default";
    public String mouthStyle = "default";
    public String accessories = "blank";
    public String facialHair = "blank";

    public AvatarState() {}

    public AvatarState(String skinColor, String hairStyle, String hairColor, String outfitColor, String expression) {
        this.skinColor = skinColor;
        this.hairStyle = hairStyle;
        this.hairColor = hairColor;
        this.outfitColor = outfitColor;
        this.expression = expression;
    }

    public AvatarState(String skinColor, String hairStyle, String hairColor, String outfitColor, String expression,
                       String eyebrowsStyle, String mouthStyle, String accessories, String facialHair) {
        this.skinColor = skinColor;
        this.hairStyle = hairStyle;
        this.hairColor = hairColor;
        this.outfitColor = outfitColor;
        this.expression = expression;
        this.eyebrowsStyle = eyebrowsStyle;
        this.mouthStyle = mouthStyle;
        this.accessories = accessories;
        this.facialHair = facialHair;
    }
}
