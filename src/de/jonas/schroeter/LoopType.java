package de.jonas.schroeter;

/**
 * Created by Jonas Schroeter on 01.06.2018 for "MediaPlayer".
 */
public enum LoopType {

    NOLOOP("de/jonas/schroeter/img/loopNot.png"),
    ALLLOOP("de/jonas/schroeter/img/loopAll.png"),
    SINGLELOOP("de/jonas/schroeter/img/loopOne.png");


    LoopType(String img){
        imgSrc = img;
    }
    private String imgSrc;

    public String getImgSrc() {
        return imgSrc;
    }
}
