package de.jonas.schroeter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Paint;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by Jonas Schroeter on 31.05.2018 for "MediaPlayer".
 */
public class Main extends Application {

    private static Gson gson = new Gson();
    private static Pane root = new Pane();
    private static Scene scene = new Scene(root, 1500, 750);
    private static Media song;
    private static MediaPlayer player;
    private static String songBefore = "file:/N:/Musik/Greenday-List/Green Day - American Eulogy.mp3".replaceAll(" ", "%20");
    private static boolean isPlaying = false;
    private static boolean isMuted = false;
    private static boolean barDragged = false;
    private static boolean randomOn = true;
    private static LoopType loop = LoopType.NOLOOP;
    private static Duration oldStop;
    private static double rate = 1;
    private static ArrayList<String> songs = new ArrayList<>();
    private static HashMap<String, String> srcToName = new HashMap<>();
    private static HashMap<String, String> nameToSrc = new HashMap<>();
    private static ArrayList<String> searchDirectories = new ArrayList<>();
    private static ArrayList<String> excludedSongs = new ArrayList<>();

    public static void main(String... args){
        launch(args);
    }

    //TODO add an "delete search directory" option - deletes the search directory and the songs
    //TODO delete - excludedSongs has to be saved as file!
    //TODO create readme.txt

    @Override
    public void start(Stage primaryStage){
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image("de/jonas/schroeter/img/paused.png"));
        primaryStage.setTitle("Media Player");
        primaryStage.show();
        root.setVisible(true);
        scene.setFill(Paint.valueOf("black"));
        primaryStage.setResizable(false);
        if(song == null){
            findMp3(primaryStage);
            createReadme();
        }
        else{
            fillList(false);
            addBar();
            addControls();
            addMenu(primaryStage);
            loadConfig();
            refresh();
        }
        root.getStyleClass().add("de/jonas/schroeter/style/style.css");
        primaryStage.setOnCloseRequest(event -> {
            saveTracks();
            saveConfig();
            System.out.println(excludedSongs);
        });

    }

    private static void findMp3(Stage primaryStage){
        try{
            File file = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
            for(File f : Objects.requireNonNull(file.listFiles())){
                if(f.getPath().contains(".mp3")){
                    song =  new Media("file:///" + f.toURI().getPath().replace(" ", "%20"));
                    player = new MediaPlayer(song);
                    fillList(false);
                    addBar();
                    addControls();
                    addMenu(primaryStage);
                    loadConfig();
                    refresh();
                    return;
                }
            }
            System.out.println("No mp3s");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Please place a Mp3 File into the jar\'s directory.");
            alert.show();
            alert.setOnCloseRequest(event -> System.exit(0));
        }
        catch(URISyntaxException e){
            System.out.println(e.getMessage());
        }
    }

    private static void loadConfig(){
        try{
            Scanner scanner = new Scanner(new File("config.json"));
            String JSON = scanner.nextLine();
            TypeToken<Object[]> typeToken = new TypeToken<Object[]>(){};
            Object[] config = gson.fromJson(JSON, typeToken.getType());
            try{
                song = new Media((String) config[0]);
                songBefore = (String) config[1];
                isMuted = (boolean) config[2];
                randomOn = (boolean) config[3];
                loop = LoopType.valueOf((String) config[4]);
                player = new MediaPlayer(song);
                double barVal = (double)config[6];
                ((Slider)scene.lookup("#bar")).setValue(barVal);
                if(config.length > 8)rate = (double)config[8];
                player.setOnReady(() -> {
                    oldStop = player.getTotalDuration().divide(100).multiply(barVal);
                    player.setVolume((double)config[5] / 100);
                    updateTime(oldStop);
                    setSongImage(song.getSource().replaceAll("file:/", "").replaceAll("%20", " "));
                    setSongInfo();
                    selectItem();
                });
                player.setMute(isMuted);
                ((ImageView)scene.lookup("#muteButton")).setImage(new Image(isMuted ? "de/jonas/schroeter/img/soundOff.png" : "de/jonas/schroeter/img/soundOn.png"));
                ((ImageView)scene.lookup("#loopButton")).setImage(new Image(loop.getImgSrc()));
                ((ImageView)scene.lookup("#randomButton")).setImage(new Image(randomOn ? "de/jonas/schroeter/img/randomOn.png" : "de/jonas/schroeter/img/randomOff.png"));
                ((Slider)scene.lookup("#volumeSlider")).setValue((double)config[5]);
            }
            catch(NullPointerException e){
                e.printStackTrace();
            }
            catch(MediaException e){
                System.out.println("config.json corrupted!");
            }
        }
        catch(FileNotFoundException e){
            System.out.println("Config File is going to be created.");
        }
    }

    private static void saveConfig(){
        Object[] config = {song.getSource(), songBefore, isMuted, randomOn, loop, ((Slider)scene.lookup("#volumeSlider")).getValue(), ((Slider)scene.lookup("#bar")).getValue(), searchDirectories.toArray(), rate};
        String JSON = gson.toJson(config);
        File jsonFile = new File("config.json");
        try{
            BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
            writer.write(JSON);
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void saveTracks(){
        Gson gson = new Gson();
        ArrayList[] arrayLists = new ArrayList[2];
        arrayLists[0] = songs;
        arrayLists[1] = excludedSongs;
        String json = gson.toJson(arrayLists);
        File file = new File("songs.json");
        BufferedWriter writer;
        try{
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(json);
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void fillList(boolean isRefresh){
        srcToName.clear();
        nameToSrc.clear();
        if(!isRefresh){
            try{
                Scanner scanner = new Scanner(new File("songs.json"));
                TypeToken<ArrayList[]> typeToken = new TypeToken<ArrayList[]>() {
                };
                //songs.addAll(gson.fromJson(scanner.nextLine(), typeToken.getType()));
                ArrayList[] songLists = gson.fromJson(scanner.nextLine(), typeToken.getType());
                excludedSongs = songLists[1];
                for(String s : (ArrayList<String>) songLists[0]){
                    if(!songs.contains(s) && !excludedSongs.contains(s)){
                        songs.add(s);
                    }
                }
            }
            catch(IOException e){
                System.out.println("JSON is going to be created.");
            }
        }
        try{
            Scanner scanner = new Scanner(new File("config.json"));
            TypeToken<Object[]> typeToken = new TypeToken<Object[]>() {
            };
            Object[] config = gson.fromJson(scanner.nextLine(), typeToken.getType());
            if(!isRefresh)searchDirectories = (ArrayList)config[7];
        }
        catch(FileNotFoundException e){
           System.out.println("Config File is going to be created.");
        }
        for(String dirPath : searchDirectories){
            File dir = new File(dirPath);
            for(File f : Objects.requireNonNull(dir.listFiles())){
                if(f.getName().contains(".mp3") && !songs.contains("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/")) && !excludedSongs.contains("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/")))
                    songs.add("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/"));
            }
            for(String s : songs){
                srcToName.put(s, s.substring(s.lastIndexOf("/") + 1, s.length()).replaceAll("%20", " ").replaceAll(".mp3", "").replaceAll("_", ""));
                nameToSrc.put(srcToName.get(s), s);
            }
        }
        if(!isRefresh)addListView();
        else refreshListView();
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
            text = (Text) scene.lookup("#artistName");
            text.setText(text.getText().replaceAll("null", "Unknown Artist"));
            text.setLayoutX((scene.getWidth() - text.getLayoutBounds().getWidth()) / 2);
            if(isPlaying && !barDragged)updateTime(null);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private static void updateTime(Duration d){
        Duration theTime = d != null ? d : player.getCurrentTime();
        Text time = (Text)scene.lookup("#textTime");
        int minutesGone = (int)theTime.toMinutes();
        int secondsGone = (int)theTime.toSeconds() - minutesGone * 60;
        int minutesFull = (int)player.getTotalDuration().toMinutes();
        int secondsFull = (int)player.getTotalDuration().toSeconds() - minutesFull * 60;

        String mgs = String.valueOf(minutesGone);
        String sgs = String.valueOf(secondsGone);
        String mfs = String.valueOf(minutesFull);
        String sfs = String.valueOf(secondsFull);

        if(mgs.length() == 1)mgs = "0" + mgs;
        if(sgs.length() == 1)sgs = "0" + sgs;
        if(mfs.length() == 1)mfs = "0" + mfs;
        if(sfs.length() == 1)sfs = "0" + sfs;

        time.setText(mgs + ":" + sgs + " / " + mfs + ":" + sfs);
        time.setLayoutX(scene.lookup("#randomButton").getLayoutX() - 15 - time.getLayoutBounds().getWidth());
    }

    private static void setSongImage(String finalSrc){
        try{
            Mp3File file = new Mp3File(new File(finalSrc));
            if(file.hasId3v2Tag()){
                byte[] imgData = file.getId3v2Tag().getAlbumImage();
                ImageView songImage = (ImageView) scene.lookup("#songImage");
                try{
                    Image img = SwingFXUtils.toFXImage(ImageIO.read(new ByteArrayInputStream(imgData)), null);
                    songImage.setImage(img);
                }
                catch(NullPointerException e){
                    songImage.setImage(new Image("de/jonas/schroeter/img/unknown.png"));
                }
            }
        }
        catch(IOException | InvalidDataException | UnsupportedTagException e){
            e.printStackTrace();
        }
    }

    private static void setSongInfo(){
        Text songName = (Text)scene.lookup("#songName");
        songName.setText(String.valueOf(song.getMetadata().get("title")));
        songName.setLayoutX((scene.getWidth() - songName.getLayoutBounds().getWidth()) / 2);

        String artistString = String.valueOf(song.getMetadata().get("artist"));
        Text artist = (Text)scene.lookup("#artistName");
        artist.setText(artistString);
        artist.setLayoutX((scene.getWidth() - artist.getLayoutBounds().getWidth()) / 2);
        artist.setVisible(true);
    }

    private static void selectItem(){
        ListView<String> view = (ListView<String>)scene.lookup("#songView");
        //TODO prevent from scrolling when double clicked
        view.scrollTo(srcToName.get(song.getSource()));
        view.getSelectionModel().select(srcToName.get(song.getSource()));
    }

    private static void playSong(double volume, boolean isMute, String src, boolean... wasStopped){
        if(song != null){
            if(!songBefore.equals(song.getSource()))songBefore = song.getSource();
        }
        else songBefore = songs.get(0);
        if(src == null)src = songs.get(0);
        String path = src;
        try{
            song = new Media(path);
        }
        catch(MediaException e){
            System.out.println("Path of Song changed!");
            songs.remove(path);
            ((ListView)scene.lookup("#songView")).getItems().remove(path.substring(path.lastIndexOf("/") + 1, path.length()).replaceAll("%20", " ").replaceAll(".mp3", "").replaceAll("_", ""));
        }
        player = new MediaPlayer(song);
        isMuted = isMute;
        String finalSrc = src.replaceAll("file:/", "").replaceAll("%20", " ");
        song.getMetadata().addListener((MapChangeListener<? super String, ? super Object>) change -> {
            setSongImage(finalSrc);
            setSongInfo();
        });
        player.setOnEndOfMedia(() -> {
            switch(loop){
                case NOLOOP:
                    if(songs.indexOf(song.getSource()) == songs.size() - 1){
                        player.stop();
                        break;
                    }
                    else{
                        oldStop = Duration.valueOf("0.0ms");
                        playSong(player.getVolume(), isMuted, checkForNextSong(song.getSource()));
                    }
                    break;
                case ALLLOOP:
                    oldStop = Duration.valueOf("0.0ms");
                    playSong(player.getVolume(), isMuted, checkForNextSong(song.getSource()));
                    break;
                case SINGLELOOP:
                    oldStop = Duration.valueOf("0.0ms");
                    playSong(player.getVolume(), isMuted, song.getSource());

            }

        });
        player.setOnReady(() ->{
            player.setVolume(volume);
            player.setMute(isMute);
            if(wasStopped.length > 1)throw new IllegalArgumentException("wasStopped should not contain more than one value");
            if(wasStopped.length == 0){
                player.play();
                player.setRate(rate); //--> speed Mult
                player.seek(oldStop);
                isPlaying = true;
            }
        });
        selectItem();
        if(wasStopped.length == 0){
            ImageView middleButton = (ImageView) scene.lookup("#middleButton");
            middleButton.setImage(new Image("de/jonas/schroeter/img/playing.png"));
        }
    }

    private static String checkForNextSong(String song){
        if(!randomOn){
            int index = songs.indexOf(song) + 1;
            index = index == songs.size() ? 0 : index;
            return songs.get(index);
        }
        else {
            Random random = new Random();
            int index = random.nextInt(songs.size());
            while(index == songs.indexOf(song)){
                index = random.nextInt(songs.size());
            }
            return songs.get(index);
        }
    }

    private static void doOnMiddleClick(ImageView middle){
        if(isPlaying){
            player.pause();
            oldStop = player.getCurrentTime();
            middle.setImage(new Image("de/jonas/schroeter/img/paused.png"));
            isPlaying = false;
        }
        else {
            if(player != null){
                double volume = ((Slider)scene.lookup("#volumeSlider")).getValue() / 100;
                playSong(volume, isMuted, song.getSource());

            }
            else playSong(1, false, null);
            middle.setImage(new Image("de/jonas/schroeter/img/playing.png"));
            isPlaying = true;
        }
    }

    private static void doOnLeftClick(){
        if(songs.size() == 0)return;
        oldStop = Duration.valueOf("0.0ms");
        if(false){ //Random?
            if(isPlaying){
                player.pause();
                if(player.getCurrentTime().toSeconds() < 6){
                    playSong(player.getVolume(), isMuted, songBefore);
                }
                else{
                    playSong(player.getVolume(), isMuted, song.getSource());
                }
            }
            else{
                playSong(player.getVolume(), isMuted, songBefore, true);
            }
        }
        else {
            if(isPlaying){
                player.pause();
                if(player.getCurrentTime().toSeconds() < 6){
                    int index = songs.indexOf(song.getSource()) - 1;
                    if(index < 0)index = songs.size() - 1;
                    playSong(player.getVolume(), isMuted, songs.get(index));
                }
                else{
                    playSong(player.getVolume(), isMuted, song.getSource());
                }
            }
            else{
                int index = songs.indexOf(song.getSource()) - 1;
                if(index < 0)index = songs.size() - 1;
                playSong(player.getVolume(), isMuted, songs.get(index), true);
            }
        }
    }

    private static void doOnRightClick(){
        if(songs.size() == 0)return;
        String src = player.getMedia().getSource();
        player.stop();
        double volume = player.getVolume();
        boolean isMute = player.isMute();
        oldStop = Duration.valueOf("0.0ms");
        if(isPlaying){
            playSong(volume, isMute, checkForNextSong(src));
        }
        else {
            playSong(volume, isMute, checkForNextSong(src), true);
        }
    }

    private static void addControls(){

        ImageView middle = new ImageView();
        middle.setImage(new Image(isPlaying ? "de/jonas/schroeter/img/playing.png" : "de/jonas/schroeter/img/paused.png"));
        middle.setId("middleButton");
        middle.setLayoutX((scene.getWidth() - middle.getImage().getWidth()) / 2);
        middle.setLayoutY(scene.getHeight() - 80);
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if(event.getCode() == KeyCode.SPACE && !scene.lookup("#searchField").isFocused()){
                doOnMiddleClick(middle);
            }
            else if((event.getCode() == KeyCode.M && !scene.lookup("#searchField").isFocused()) ||event.getCode() == KeyCode.F8){
                if(isMuted){
                    player.setMute(false);
                }
                else {
                    player.setMute(true);
                }
                isMuted = !isMuted;
                ((ImageView)scene.lookup("#muteButton")).setImage(new Image(isMuted ? "de/jonas/schroeter/img/soundOff.png" : "de/jonas/schroeter/img/soundOn.png"));
            }
            else if(event.getCode() == KeyCode.F5){
                doOnLeftClick();
            }
            else if(event.getCode() == KeyCode.F7){
                doOnRightClick();
            }
        });
        middle.setOnMouseClicked(event -> doOnMiddleClick(middle));

        ImageView left = new ImageView();
        left.setImage(new Image("de/jonas/schroeter/img/back.png"));
        left.setLayoutX(middle.getLayoutX() - left.getImage().getWidth() - 15);
        left.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - left.getImage().getHeight() / 2);
        left.setOnMouseClicked(event -> doOnLeftClick());
        ImageView right = new ImageView();
        right.setImage(new Image("de/jonas/schroeter/img/forwards.png"));
        right.setLayoutX(middle.getLayoutX() + middle.getImage().getWidth() + 15);
        right.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - right.getImage().getHeight() / 2);
        right.setOnMouseClicked(event -> doOnRightClick());
        ImageView muteButton = new ImageView();
        muteButton.setId("muteButton");
        muteButton.setImage(new Image(isMuted ? "de/jonas/schroeter/img/soundOff.png" : "de/jonas/schroeter/img/soundOn.png"));
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
        loopButton.setId("loopButton");
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
        randomButton.setId("randomButton");
        randomButton.setImage(new Image(randomOn ? "de/jonas/schroeter/img/randomOn.png" : "de/jonas/schroeter/img/randomOff.png"));
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
        slider.setValue(player.getVolume() * 100);
        slider.setPrefSize(100, 10);
        slider.setLayoutX(muteButton.getImage().getWidth() + muteButton.getLayoutX() + 15);
        slider.setLayoutY(muteButton.getLayoutY() + muteButton.getImage().getHeight() / 2 - slider.getPrefHeight() / 2);
        slider.setOnMouseDragged(event -> player.setVolume(slider.getValue() / 100));
        slider.setOnMouseClicked(event -> player.setVolume(slider.getValue() / 100));
        slider.setId("volumeSlider");
        slider.setStyle("-fx-faint-focus-color: transparent; -fx-focus-color: grey");

        Text songName = new Text();
        songName.setId("songName");
        songName.setTextAlignment(TextAlignment.CENTER);
        songName.setFont(Font.font("Times New Roman", FontWeight.BOLD, FontPosture.ITALIC, 30));
        songName.setLayoutY(100);

        Text artist = new Text();
        artist.setId("artistName");
        artist.setTextAlignment(TextAlignment.CENTER);
        artist.setFont(Font.font("Times New Roman", FontWeight.BLACK, FontPosture.ITALIC, 23));
        artist.setLayoutY(140);

        Text time = new Text();
        time.setId("textTime");
        time.setText("00:00 / 00:00");
        time.setFill(Paint.valueOf("white"));
        time.setTextAlignment(TextAlignment.CENTER);
        time.setLayoutY(left.getLayoutY() + left.getImage().getHeight() - time.getLayoutBounds().getHeight() / 2);

        ImageView songImage = new ImageView();
        songImage.setLayoutX(400);
        songImage.setId("songImage");
        int radius = 400;
        songImage.setFitHeight(radius);
        songImage.setFitWidth(radius);
        songImage.setLayoutX((scene.getWidth() - radius) / 2);
        songImage.setLayoutY(songName.getLayoutY() + 60);
        songImage.setStyle("-fx-border-width: 10; -fx-border-color: red");

        ProgressBar progressBar = new ProgressBar();
        Slider bar = (Slider)scene.lookup("#bar");
        progressBar.setLayoutX(bar.getLayoutX());
        progressBar.setLayoutY(bar.getLayoutY());
        progressBar.setVisible(true);
        progressBar.setProgress(100);

        ImageView underBar = new ImageView();
        underBar.setImage(new Image("de/jonas/schroeter/img/underBar.png"));
        underBar.setFitWidth(scene.getWidth());
        underBar.setFitHeight(scene.getHeight() - scene.lookup("#bar").getLayoutY() + 10);
        underBar.setLayoutY(scene.lookup("#bar").getLayoutY() - 10);

        TextField searchField = new TextField();
        searchField.setStyle("-fx-faint-focus-color: transparent; -fx-focus-color: grey");
        searchField.setId("searchField");
        ListView<String> view = (ListView<String>)scene.lookup("#songView");
        searchField.setPrefWidth(200);
        searchField.setPrefHeight(25);
        searchField.setLayoutX(2);
        searchField.setPromptText("Search");
        searchField.setLayoutY(view.getLayoutY() - searchField.getPrefHeight() - 10);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if(searchField.getText().equals("")){
                selectItem();
                return;
            }
            for(String s : view.getItems()){
                if(s.contains(searchField.getText())){
                    view.getSelectionModel().select(s);
                    view.scrollTo(s);
                    break;
                }
                else {
                    view.getSelectionModel().clearSelection();
                }
            }
        });
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if(event.getCode() == KeyCode.ENTER){
                oldStop = Duration.valueOf("0.0ms");
                if(player != null){
                    player.stop();
                    playSong(player.getVolume(), player.isMute(), nameToSrc.get(view.getSelectionModel().getSelectedItem()));
                }
                else playSong(((Slider) scene.lookup("#volumeSlider")).getValue(), false, nameToSrc.get(view.getSelectionModel().getSelectedItem()));
            }
            else if(event.getCode() == KeyCode.TAB){
                selectItem();
            }
        });

        root.getChildren().addAll(left, middle, right, muteButton, loopButton, randomButton, slider, songName, artist, time, songImage, underBar, searchField);
        underBar.toBack();
    }

    private static void addBar(){
        Slider bar = new Slider();
        bar.setPrefWidth(scene.getWidth() - 80);
        bar.setLayoutX((scene.getWidth() - bar.getPrefWidth()) / 2);
        bar.setLayoutY(scene.getHeight() - 100);
        bar.setId("bar");
        bar.setStyle("-fx-faint-focus-color: transparent; -fx-focus-color: grey");
        // SP * (FT / 100) = CT
        bar.setOnDragDetected(event -> barDragged = true);

        bar.setOnMouseDragged(event -> updateTime(player.getTotalDuration().divide(100).multiply(bar.getValue())));
        bar.setOnMouseReleased(event -> {
            if(player != null){
                oldStop = player.getTotalDuration().divide(100).multiply(bar.getValue());
                player.seek(player.getTotalDuration().divide(100).multiply(bar.getValue()));
                updateTime(player.getTotalDuration().divide(100).multiply(bar.getValue()));
            }
            barDragged = false;
        });
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(bar.getPrefWidth());
        progressBar.setLayoutX(bar.getLayoutX());
        progressBar.setLayoutY(bar.getLayoutY());
        progressBar.setPrefHeight(10);
        progressBar.setProgress(80);
        progressBar.setId("progressBar");
        progressBar.setStyle("-fx-border-color: black; -fx-fill: red;");

        root.getChildren().addAll(bar);
    }

    private static void refreshListView(){
        ObservableList<String> names = FXCollections.observableArrayList();
        for(String s : songs){
            names.add(srcToName.get(s));
        }
        ListView<String> view = ((ListView<String>)scene.lookup("#songView"));
        view.getItems().clear();
        view.getItems().addAll(names);
    }

    private static void addListView(){
        ObservableList<String> names = FXCollections.observableArrayList();
        for(String s : songs){
            names.add(srcToName.get(s));
        }
        ListView<String> view = new ListView<>(names);
        view.setCellFactory(param -> {
            ListCell<String> cell = new ListCell<>();
            cell.setId("cell");
            cell.textProperty().bind(cell.itemProperty());
            ContextMenu contextMenu = new ContextMenu();
            contextMenu.setOnAutoHide(event -> selectItem());

            MenuItem delete = new MenuItem("Delete");
            delete.setOnAction(event -> {
                songs.remove(nameToSrc.get(cell.getItem()));
                System.out.println(nameToSrc.get(cell.getItem()));
                excludedSongs.add(nameToSrc.get(cell.getItem()));
                fillList(true);
            });

            contextMenu.getItems().add(delete);
            cell.setContextMenu(contextMenu);
            return cell;
        });
        view.setId("songView");
        view.setPrefWidth(350);
        view.setPrefHeight(500);
        view.setLayoutY(100);
        view.setStyle("-fx-faint-focus-color: transparent; -fx-focus-color: transparent; -fx-border-color: black");
        view.setOnMouseClicked(event -> {
            if(event.getClickCount() == 2 && event.getButton().equals(MouseButton.PRIMARY)){
                oldStop = Duration.valueOf("0.0ms");
                if(player != null){
                    player.stop();
                    double volume = player.getVolume();
                    boolean isMute = player.isMute();
                    playSong(volume, isMute, nameToSrc.get(view.getSelectionModel().getSelectedItem()));
                }
                else playSong(((Slider) scene.lookup("#volumeSlider")).getValue(), false, nameToSrc.get(view.getSelectionModel().getSelectedItem()));
            }
            else if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 1){
                if(song == null)view.getSelectionModel().clearSelection();
                else view.getSelectionModel().select(srcToName.get(song.getSource()));
            }
        });
        root.getChildren().add(view);
    }

    private static void addMenu(Stage primaryStage){
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle("-fx-border-color: black");

        Menu files = new Menu("Files");
        MenuItem search = new MenuItem("Add Search Directory");
        search.setOnAction(event -> {

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Search");
            dialog.setHeaderText("Please enter the directory path:");
            String s = String.valueOf(dialog.showAndWait());
            s = s.replace("Optional[", "").replace("]", "").replace("\\", "/");
            if(s.equals("Optional.empty"))return;
            if(!s.equals("") && Files.exists(Paths.get(s))) searchDirectories.add(s);
                else {
                    createDirNotExist(s);
            }
            fillList(true);
        });
        MenuItem file = new MenuItem("Add File");
        file.setOnAction(event -> setupFileChooser(primaryStage));

        MenuItem deleteDir = new MenuItem("Remove Search Directory");
        deleteDir.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Remove");
            dialog.setHeaderText("Please enter the directory path:");
            String s = String.valueOf(dialog.showAndWait());
            s = s.replace("Optional[", "").replace("]", "").replace("\\", "/");
            if(s.equals("Optional.empty"))return;
            if(!s.equals("") && Files.exists(Paths.get(s))){
                System.out.println("Remove!");
                /*File dir = new File(s);
                for(File f : Objects.requireNonNull(dir.listFiles())){
                    songs.remove(f.toPath().toString());
                }
                searchDirectories.remove(s);*/
            }
            else {
                createDirNotExist(s);
            }
            fillList(true);
        });
        files.getItems().addAll(search, file);

        Menu options = new Menu("Options");
        MenuItem speedMult = new Menu("Speed");
        speedMult.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Speed");
            dialog.setHeaderText("The playing multiplier is currently " + player.getRate());
            try{
                String s = String.valueOf(dialog.showAndWait()).replace("Optional[", "").replace("]", "");
                System.out.println(s);
                double rate = Double.parseDouble(s);
                if(rate < 0 || rate > 8){
                    player.setRate(rate < 0 ? 0 : 8);
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Warning");
                    alert.setHeaderText("The chosen value has to be between 0.0 and 8.0.");
                    alert.show();
                    return;
                }
                player.setRate(rate);
                Main.rate = rate;
            }
            catch(NumberFormatException e){
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Please input numbers only.");
                alert.show();
                System.out.println("Illegal Argument!");
            }
        });
        options.getItems().add(speedMult);

        menuBar.getMenus().addAll(files, options);
        root.getChildren().add(menuBar);
    }

    private static void setupFileChooser(Stage primaryStage){
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose File");
        chooser.setInitialDirectory(new File(System.getProperty("user.home") + "/documents"));
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("MP3 Audio File", "*.mp3"));

        File file = chooser.showOpenDialog(primaryStage);
        if(file == null){return;}
        songs.add("file:/" + file.toPath().toAbsolutePath().toString().replace("\\", "/").replace(" ", "%20"));
        excludedSongs.remove("file:/" + file.toPath().toAbsolutePath().toString().replace("\\", "/").replace(" ", "%20"));
        fillList(true);
    }

    private static void createDirNotExist(String s){
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText("The directory \"" + s + "\" does not exist.");
        alert.show();
    }

    private static void createReadme(){
        File file = new File("readme.txt");
        try{
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write("First run in this directory: " + LocalDateTime.now().toLocalDate().toString());
            writer.newLine();
            writer.write("Developer: Jonas Schröter");
            writer.newLine();
            writer.write("https://lazybone2017.github.io/");
            writer.newLine();
            writer.newLine();
            writer.write("For the first time, a MP3 file is needed in the jar\'s directory.");
            writer.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }

    }
}
