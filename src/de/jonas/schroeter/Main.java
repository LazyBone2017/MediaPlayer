package de.jonas.schroeter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by Jonas Schroeter on 31.05.2018 for "MediaPlayer".
 */
public class Main extends Application {

    private static Stage primaryStage;
    public static Pane root = new Pane();
    public static Scene scene = new Scene(root, 1500, 750);
    private static MediaPlayer player;
    private static Media song;
    private static LocalDateTime time = LocalDateTime.now();
    private static boolean isPlaying = false;
    private static boolean isMuted = false;
    private static boolean barDragged = false;
    private static boolean randomOn = false;
    private static LoopType loop = LoopType.NOLOOP;
    private static ArrayList<String> songs = new ArrayList<>();
    private static HashMap<String, String> srcToName = new HashMap<>();
    private static HashMap<String, String> nameToSrc = new HashMap<>();
    private static String searchDirectory = "N:/Musik/Musikablage";

    public static void main(String... args){
        launch(args);
    }

    @Override
    public void start(Stage primaryStage){
        this.primaryStage = primaryStage;
        primaryStage.setScene(scene);
        primaryStage.show();
        root.setVisible(true);
        primaryStage.setResizable(false);
        refresh();
        fillList();
        addControls();
        addBar();
        //playSong(1, false, null);
        primaryStage.setOnCloseRequest(event -> saveTracks());
    }

    private static void saveTracks(){
        Gson gson = new Gson();
        String json = gson.toJson(songs);
        File file = new File("songs.json");
        BufferedWriter writer = null;
        try{
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(json);
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void fillList(){
        Gson gson = new Gson();
        try{
            Scanner scanner = new Scanner(new File("songs.json"));
            TypeToken<ArrayList<String>> typeToken = new TypeToken<ArrayList<String>>() {};
            songs.addAll(gson.fromJson(scanner.nextLine(), typeToken.getType()));
        }
        catch(IOException e){
            System.out.println("JSON is going to be created.");
        }
        File dir = new File(searchDirectory);
        for(File f : Objects.requireNonNull(dir.listFiles())){
            if(f.getName().contains(".mp3") && !songs.contains("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/")))songs.add("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/"));
        }
        for(String s : songs){
            srcToName.put(s, s.substring(s.lastIndexOf("/") + 1, s.length()).replaceAll("%20", " ").replaceAll(".mp3", "").replaceAll("_", ""));
            nameToSrc.put(srcToName.get(s), s);
        }
        addListView();
    }

    private static void refresh(){
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(100), event -> {
            if(isPlaying && !barDragged){
                Slider bar = (Slider) scene.lookup("#bar");
                double songPercentage = player.getCurrentTime().toSeconds() / (player.getTotalDuration().toSeconds() / 100);
                bar.setValue(songPercentage);
            }
            Text text = (Text) scene.lookup("#songName");
            text.setText(text.getText().replaceAll("null", "Unknown"));
            text.setLayoutX((scene.getWidth() - text.getLayoutBounds().getWidth()) / 2);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private static void playSong(double volume, boolean isMute, String src){
        if(src == null)src = songs.get(0);
        String path = src;
        song = new Media(path);
        player = new MediaPlayer(song);
        player.setVolume(volume);
        player.setMute(isMute);
        isMuted = isMute;
        song.getMetadata().addListener((MapChangeListener<? super String, ? super Object>) change -> {
            Text text = (Text)scene.lookup("#songName");
            String artist = String.valueOf(song.getMetadata().get("artist"));
            text.setText(artist + " - " + String.valueOf(song.getMetadata().get("title")));
            text.setLayoutX((scene.getWidth() - text.getLayoutBounds().getWidth()) / 2);
        });
        player.setOnEndOfMedia(() -> {
            switch(loop){
                case NOLOOP:
                    if(songs.indexOf(song.getSource()) == songs.size() - 1){
                        player.stop();
                        break;
                    }
                    else playSong(player.getVolume(), isMuted, checkForNextSong(song.getSource()));
                    break;
                case ALLLOOP:
                    playSong(player.getVolume(), isMuted, checkForNextSong(song.getSource()));
                    break;
                case SINGLELOOP:
                    System.out.println(song.getSource());
                    playSong(player.getVolume(), isMuted, song.getSource());
            }

        });
        player.play();
        isPlaying = true;
        ListView<String> view = (ListView<String>)scene.lookup("#songView");
        view.getSelectionModel().select(srcToName.get(song.getSource()));
        ImageView middleButton = (ImageView)scene.lookup("#middleButton");
        middleButton.setImage(new Image("de/jonas/schroeter/img/playing.png"));

        time = LocalDateTime.now();
    }

    private static String checkForNextSong(String song){
        int index = songs.indexOf(song) + 1;
        index = index == songs.size() ? 0 : index;
        return songs.get(index);
    }
    private static void addControls(){

        ImageView middle = new ImageView();
        middle.setImage(new Image("de/jonas/schroeter/img/paused.png"));
        middle.setId("middleButton");
        middle.setLayoutX((scene.getWidth() - middle.getImage().getWidth()) / 2);
        middle.setLayoutY(scene.getHeight() - 80);
        middle.setOnMouseClicked(event -> {
            if(isPlaying){
                player.pause();
                middle.setImage(new Image("de/jonas/schroeter/img/paused.png"));
                isPlaying = false;
            }
            else {
                if(player != null)player.play();
                else playSong(1, false, null);
                middle.setImage(new Image("de/jonas/schroeter/img/playing.png"));
                isPlaying = true;
            }
        });

        ImageView left = new ImageView();
        left.setImage(new Image("de/jonas/schroeter/img/back.png"));
        left.setLayoutX(middle.getLayoutX() - left.getImage().getWidth() - 15);
        left.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - left.getImage().getHeight() / 2);
        left.setOnMouseClicked(event -> {
            if(isPlaying){
                //TODO implement
            }
            else {

            }
        });
        ImageView right = new ImageView();
        right.setImage(new Image("de/jonas/schroeter/img/forwards.png"));
        right.setLayoutX(middle.getLayoutX() + middle.getImage().getWidth() + 15);
        right.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - right.getImage().getHeight() / 2);
        right.setOnMouseClicked(event -> {
            String src = player.getMedia().getSource();
            player.stop();
            double volume = player.getVolume();
            boolean isMute = player.isMute();
            playSong(volume, isMute, checkForNextSong(src));
        });
        ImageView muteButton = new ImageView();
        muteButton.setImage(new Image("de/jonas/schroeter/img/soundOn.png"));
        muteButton.setLayoutX(right.getImage().getWidth() + right.getLayoutX() + 15);
        muteButton.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - muteButton.getImage().getHeight() / 2);
        muteButton.setOnMouseClicked(event -> {
            if(isMuted){
                player.setMute(false);
                isMuted = false;
                muteButton.setImage(new Image("de/jonas/schroeter/img/soundOn.png"));
            }
            else {
                player.setMute(true);
                isMuted = true;
                muteButton.setImage(new Image("de/jonas/schroeter/img/soundOff.png"));
            }
        });
        ImageView loopButton = new ImageView();
        loopButton.setImage(new Image(loop.getImgSrc()));
        loopButton.setLayoutX(left.getLayoutX() - 15 - loopButton.getImage().getWidth());
        loopButton.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - loopButton.getImage().getHeight() / 2);
        loopButton.setOnMouseClicked(event -> {
            int index = Arrays.asList(LoopType.values()).indexOf(loop) + 1;
            index = index == LoopType.values().length ? 0 : index;
            loop = LoopType.values()[index];
            loopButton.setImage(new Image(loop.getImgSrc()));
        });
        ImageView randomButton = new ImageView();
        randomButton.setImage(new Image("de/jonas/schroeter/img/randomOff.png"));
        randomButton.setLayoutX(loopButton.getLayoutX() - 15 - randomButton.getImage().getWidth());
        randomButton.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - randomButton.getImage().getHeight() / 2);
        randomButton.setOnMouseClicked(event -> {
            if(randomOn){
                randomButton.setImage(new Image("de/jonas/schroeter/img/randomOff.png"));
            }
            else randomButton.setImage(new Image("de/jonas/schroeter/img/randomOn.png"));
            randomOn = !randomOn;
        });

        Slider slider = new Slider();
        slider.setMax(100);
        slider.setMin(0);
        slider.setValue(100);
        slider.setPrefSize(100, 10);
        slider.setLayoutX(muteButton.getImage().getWidth() + muteButton.getLayoutX() + 15);
        slider.setLayoutY(muteButton.getLayoutY() + muteButton.getImage().getHeight() / 2 - slider.getPrefHeight() / 2);
        slider.setOnMouseDragged(event -> player.setVolume(slider.getValue() / 100));
        slider.setOnMouseClicked(event -> player.setVolume(slider.getValue() / 100));
        slider.setId("volumeSlider");

        Text text = new Text();
        text.setId("songName");
        text.setTextAlignment(TextAlignment.CENTER);
        text.setLayoutY(100);

        root.getChildren().addAll(left, middle, right, muteButton, loopButton, randomButton, slider, text);
    }
    private static void addBar(){
        Slider bar = new Slider();
        bar.setPrefWidth(scene.getWidth() - 80);
        bar.setLayoutX((scene.getWidth() - bar.getPrefWidth()) / 2);
        bar.setLayoutY(scene.getHeight() - 100);
        bar.setId("bar");
        root.getChildren().add(bar);
        // SP * (FT / 100) = CT
        bar.setOnDragDetected(event -> {
            barDragged = true;
        });
        bar.setOnMouseClicked(Event::consume);
        bar.setOnMouseReleased(event -> {
            if(player != null)player.seek(player.getTotalDuration().divide(100).multiply(bar.getValue()));
            barDragged = false;
            System.out.println("Release");
        });

    }
    private static void addListView(){
        ObservableList<String> names = FXCollections.observableArrayList();
        for(String s : songs){
            names.add(srcToName.get(s));
        }
        ListView<String> view = new ListView<>(names);
        view.setId("songView");
        view.setPrefWidth(300);
        view.setOnMouseClicked(event -> {
            if(event.getClickCount() == 2){
                if(player != null){
                    player.stop();
                    double volume = player.getVolume();
                    boolean isMute = player.isMute();
                    playSong(volume, isMute, nameToSrc.get(view.getSelectionModel().getSelectedItem()));
                }
                else
                    playSong(((Slider) scene.lookup("#volumeSlider")).getValue(), false, nameToSrc.get(view.getSelectionModel().getSelectedItem()));
            }
            if(song == null)view.getSelectionModel().clearSelection();
            else view.getSelectionModel().select(srcToName.get(song.getSource()));
        });
        System.out.println(view.getItems().size());
        root.getChildren().add(view);
    }
}
