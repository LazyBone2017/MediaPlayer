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
import javafx.scene.control.*;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Jonas Schroeter on 31.05.2018 for "MediaPlayer".
 */
public class Main extends Application {

    private static Gson gson = new Gson();
    public static Pane root = new Pane();
    public static Scene scene = new Scene(root, 1500, 750);
    private static Media song = new Media("file:/N:/Musik/Greenday-List/Green Day - American Idiot.mp3".replaceAll(" ", "%20"));
    private static MediaPlayer player = new MediaPlayer(song);
    private static String songBefore = "file:/N:/Musik/Greenday-List/Green Day - American Eulogy.mp3".replaceAll(" ", "%20");
    private static boolean isPlaying = false;
    private static boolean isMuted = false;
    private static boolean barDragged = false;
    private static boolean randomOn = false;
    private static LoopType loop = LoopType.NOLOOP;
    private static Duration oldStop;
    private static ArrayList<String> songs = new ArrayList<>();
    private static HashMap<String, String> srcToName = new HashMap<>();
    private static HashMap<String, String> nameToSrc = new HashMap<>();
    private static ArrayList<String> searchDirectories = new ArrayList<>();

    public static void main(String... args){
        launch(args);
    }

    @Override
    public void start(Stage primaryStage){
        primaryStage.setScene(scene);
        primaryStage.show();
        root.setVisible(true);
        primaryStage.setResizable(false);
        refresh();
        fillList(false);
        addControls();
        addBar();
        addMenu(primaryStage);
        loadConfig();
        primaryStage.setOnCloseRequest(event -> {
            saveTracks();
            saveConfig();
        });
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
                player.setOnReady(() -> {
                    oldStop = player.getTotalDuration().divide(100).multiply(barVal);
                    updateTime(oldStop);
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
        Object[] config = {song.getSource(), songBefore, isMuted, randomOn, loop, ((Slider)scene.lookup("#volumeSlider")).getValue(), ((Slider)scene.lookup("#bar")).getValue(), searchDirectories.toArray()};
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

    private static void fillList(boolean isRefresh){
        srcToName.clear();
        nameToSrc.clear();
        try{
            Scanner scanner = new Scanner(new File("songs.json"));
            TypeToken<ArrayList<String>> typeToken = new TypeToken<ArrayList<String>>() {};
            //songs.addAll(gson.fromJson(scanner.nextLine(), typeToken.getType()));
            for(String s : (ArrayList<String>)gson.fromJson(scanner.nextLine(), typeToken.getType())){
                if(!songs.contains(s))songs.add(s);
            }
        }
        catch(IOException e){
            System.out.println("JSON is going to be created.");
        }
        try{
            Scanner scanner = new Scanner(new File("config.json"));
            TypeToken<Object[]> typeToken = new TypeToken<Object[]>() {
            };
            Object[] config = gson.fromJson(scanner.nextLine(), typeToken.getType());
            if(!isRefresh)searchDirectories = (ArrayList)config[7];
        }
        catch(FileNotFoundException e){
            e.printStackTrace();
        }
        for(String dirPath : searchDirectories){
            File dir = new File(dirPath);
            for(File f : Objects.requireNonNull(dir.listFiles())){
                if(f.getName().contains(".mp3") && !songs.contains("file:/" + f.getAbsolutePath().replace(" ", "%20").replace("\\", "/")))
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

    private static void playSong(double volume, boolean isMute, String src){
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
            player.play();
            player.seek(oldStop);
            isPlaying = true;
        });


        ListView<String> view = (ListView<String>)scene.lookup("#songView");
        view.getSelectionModel().select(srcToName.get(song.getSource()));
        ImageView middleButton = (ImageView)scene.lookup("#middleButton");
        middleButton.setImage(new Image("de/jonas/schroeter/img/playing.png"));
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

    private static void addControls(){

        ImageView middle = new ImageView();
        middle.setImage(new Image(isPlaying ? "de/jonas/schroeter/img/playing.png" : "de/jonas/schroeter/img/paused.png"));
        middle.setId("middleButton");
        middle.setLayoutX((scene.getWidth() - middle.getImage().getWidth()) / 2);
        middle.setLayoutY(scene.getHeight() - 80);
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if(event.getCode() == KeyCode.SPACE){
                doOnMiddleClick(middle);
            }
        });
        middle.setOnMouseClicked(event -> {
            doOnMiddleClick(middle);
        });

        ImageView left = new ImageView();
        left.setImage(new Image("de/jonas/schroeter/img/back.png"));
        left.setLayoutX(middle.getLayoutX() - left.getImage().getWidth() - 15);
        left.setLayoutY(middle.getLayoutY() + middle.getImage().getHeight() / 2 - left.getImage().getHeight() / 2);
        left.setOnMouseClicked(event -> {
            if(randomOn){
                if(isPlaying){
                    player.stop();
                    if(player.getCurrentTime().toSeconds() < 6){
                        playSong(player.getVolume(), isMuted, songBefore);
                        System.out.println("smaller");
                    }
                    else{
                        System.out.println("Isnt playing");
                        playSong(player.getVolume(), isMuted, song.getSource());
                    }
                }
                else{
                    playSong(player.getVolume(), isMuted, song.getSource());
                }
            }
            else {
                if(isPlaying){
                    player.stop();
                    if(player.getCurrentTime().toSeconds() < 6){
                        int index = songs.indexOf(song.getSource()) - 1;
                        if(index < 0)index = songs.size() - 1;
                        playSong(player.getVolume(), isMuted, songs.get(index));
                        System.out.println("smaller");
                    }
                    else{
                        System.out.println("Isnt playing");
                        playSong(player.getVolume(), isMuted, song.getSource());
                    }
                }
                else{
                    int index = songs.indexOf(song.getSource()) - 1;
                    if(index < 0)index = songs.size() - 1;
                    playSong(player.getVolume(), isMuted, songs.get(index));
                }
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
            oldStop = Duration.valueOf("0.0ms");
            playSong(volume, isMute, checkForNextSong(src));
        });
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
        slider.setOnMouseDragged(event -> {
            player.setVolume(slider.getValue() / 100);
        });
        slider.setOnMouseClicked(event -> {
            player.setVolume(slider.getValue() / 100);
        });
        slider.setId("volumeSlider");

        Text text = new Text();
        text.setId("songName");
        text.setTextAlignment(TextAlignment.CENTER);
        text.setLayoutY(100);

        Text time = new Text();
        time.setId("textTime");
        time.setText("00:00 / 00:00");
        time.setTextAlignment(TextAlignment.CENTER);
        time.setLayoutY(left.getLayoutY() + left.getImage().getHeight() - time.getLayoutBounds().getHeight() / 2);

        root.getChildren().addAll(left, middle, right, muteButton, loopButton, randomButton, slider, text, time);
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

        bar.setOnMouseDragged(event -> {
            updateTime(player.getTotalDuration().divide(100).multiply(bar.getValue()));
            System.out.println("Dragged!");
        });
        bar.setOnMouseReleased(event -> {
            if(player != null){
                oldStop = player.getTotalDuration().divide(100).multiply(bar.getValue());
                player.seek(player.getTotalDuration().divide(100).multiply(bar.getValue()));
                updateTime(player.getTotalDuration().divide(100).multiply(bar.getValue()));
            }
            barDragged = false;
            System.out.println("Release");
        });

    }

    private static void refreshListView(){
        ObservableList<String> names = FXCollections.observableArrayList();
        for(String s : songs){
            names.add(srcToName.get(s));
        }
        ListView<String> view = ((ListView<String>)scene.lookup("#songView"));
        view.getItems().clear();
        System.out.println(view.getItems().size());
        System.out.println(names.contains("Kalimba"));
        view.getItems().addAll(names);
        System.out.println(view.getItems().size());
    }

    private static void addListView(){
        ObservableList<String> names = FXCollections.observableArrayList();
        for(String s : songs){
            names.add(srcToName.get(s));
        }
        ListView<String> view = new ListView<>(names);
        view.setCellFactory(param -> {
            ListCell<String> cell = new ListCell<>();
            cell.textProperty().bind(cell.itemProperty());
            ContextMenu contextMenu = new ContextMenu();
            contextMenu.setOnAutoHide(event -> {
                view.getSelectionModel().select(srcToName.get(song.getSource()));
            });

            MenuItem delete = new MenuItem("Delete");
            delete.setOnAction(event -> {
                songs.remove(nameToSrc.get(cell.getItem()));
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
            else if(event.getButton().equals(MouseButton.SECONDARY)){

            }
            else if(event.getButton().equals(MouseButton.PRIMARY)){
                if(song == null)view.getSelectionModel().clearSelection();
                else view.getSelectionModel().select(srcToName.get(song.getSource()));
            }
        });
        root.getChildren().add(view);
    }

    private static void addMenu(Stage primaryStage){
        MenuBar menuBar = new MenuBar();
        Menu files = new Menu("Files");
        MenuItem search = new MenuItem("Add Search Directory");
        search.setOnAction(event -> {
            String s = JOptionPane.showInputDialog("Please enter the directory path:");
            if(s == null)return;
            s = s.replace("\\", "/");
            if(Files.exists(Paths.get(s))) searchDirectories.add(s);
                else JOptionPane.showMessageDialog(null, "The directory \"" + s + "\" does not exist.");
                System.out.println("Implement that shitty stuff!");
                fillList(true);
        });
        MenuItem file = new MenuItem("Add File");
        file.setOnAction(event -> {
            setupFileChooser(primaryStage);
        });

        files.getItems().addAll(search, file);
        menuBar.getMenus().add(files);
        root.getChildren().add(menuBar);
    }

    private static void setupFileChooser(Stage primaryStage){
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose File");
        chooser.setInitialDirectory(new File(System.getProperty("user.home") + "/documents"));
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("MP3 Audio File", "*.mp3"));

        File file = chooser.showOpenDialog(primaryStage);
        if(file == null)return;
        songs.add("file:/" + file.toPath().toAbsolutePath().toString().replace("\\", "/").replace(" ", "%20"));
        fillList(true);
    }
}
