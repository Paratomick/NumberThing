package main;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.*;

public class Main extends Application {

    public static boolean end = false;

    Stage stage;
    GridPane root;

    NumberBox selectedBox;
    List<NumberBox> numberBoxList;
    private int tileNumber;

    private NetworkConnection connection = null;

    private FileChooser fileChooser;

    private boolean loading = false;
    private BooleanProperty hasNumpad, isSudoku;

    @Override
    public void start(Stage primaryStage) throws Exception{
        stage = primaryStage;
        root = new GridPane();
        primaryStage.setTitle("Number Thing");
        Scene scene = new Scene(root, 300, 275);
        root.setAlignment(Pos.CENTER);
        scene.getStylesheets().add("main/style.css");
        primaryStage.setScene(scene);

        fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Save to Resource File");
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("SaveFile (*.save)", "*.save");
        fileChooser.getExtensionFilters().add(filter);

        numberBoxList = new ArrayList<>();

        hasNumpad = new SimpleBooleanProperty(true);
        isSudoku = new SimpleBooleanProperty(true);

        loadMenu();
        createListeners(scene);

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if(connection != null)
            connection.closeConnection();
    }

    private void loadMenu() {
        root.getChildren().clear();
        numberBoxList.clear();

        VBox menuBox = new VBox(5);
        VBox lowerMenuBox = new VBox(5);
        RadioButton radioButton1 = new RadioButton("Host");
        RadioButton radioButton2 = new RadioButton("Client");

        ToggleGroup radioGroup = new ToggleGroup();

        radioButton1.setToggleGroup(radioGroup);
        radioButton2.setToggleGroup(radioGroup);

        BooleanProperty buttonDeact = new SimpleBooleanProperty(false);

        Label menuHostPortLabel = new Label("Port:");
        TextField menuHostPortTextField = new TextField("55555");
        Label menuTilesLabel = new Label("Felder:");
        TextField menuTilesTextField = new TextField("9");
        Button menuHostButton = new Button("Start");
        menuHostButton.disableProperty().bind(menuHostPortTextField.textProperty().isEmpty().or(menuTilesTextField.textProperty().isEmpty()).or(buttonDeact));

        String[] tilesText = new String[1];
        tilesText[0] = "9";
        menuTilesTextField.disableProperty().bind(isSudoku);
        isSudoku.addListener((observable, oldValue, newValue) -> {
            if(newValue) {
                tilesText[0] = menuTilesTextField.textProperty().get();
                menuTilesTextField.textProperty().setValue("9");
            } else {
                menuTilesTextField.textProperty().setValue(tilesText[0]);
            }
        });

        Label menuClientIPLabel = new Label("IP:");
        TextField menuClientIPTextField = new TextField("127.0.0.1");
        Label menuClientPortLabel = new Label("Port:");
        TextField menuClientPortTextField = new TextField("55555");
        Button menuClientButton = new Button("Start");
        menuClientButton.disableProperty().bind(menuClientIPTextField.textProperty().isEmpty().or(menuClientPortTextField.textProperty().isEmpty()).or(buttonDeact));

        CheckBox checkBoxNumPad = new CheckBox("Numpad aktiv.");
        checkBoxNumPad.selectedProperty().bindBidirectional(hasNumpad);
        CheckBox checkBoxSudoku = new CheckBox("Sudoku.");
        checkBoxSudoku.selectedProperty().bindBidirectional(isSudoku);

        menuBox.getChildren().addAll(radioButton1, radioButton2, checkBoxNumPad, checkBoxSudoku, lowerMenuBox);

        radioButton1.setOnAction(e -> {
            lowerMenuBox.getChildren().clear();

            lowerMenuBox.getChildren().addAll(menuHostPortLabel, menuHostPortTextField, menuTilesLabel, menuTilesTextField, menuHostButton);
        });

        radioButton2.setOnAction(e -> {
            lowerMenuBox.getChildren().clear();

            lowerMenuBox.getChildren().addAll(menuClientIPLabel, menuClientIPTextField, menuClientPortLabel, menuClientPortTextField, menuClientButton);
        });

        //Start Host
        menuHostButton.setOnAction(e -> {
            stage.setTitle("Number Thing: Server");
            connection = createServer(Integer.parseInt(menuHostPortTextField.getText()));
            try {
                connection.startConnection();
            } catch (Exception e1) {e1.printStackTrace();}
            loadBox(Integer.parseInt(menuTilesTextField.getText()));
            sendAll();
        });

        //Start Client
        menuClientButton.setOnAction(e -> {
            stage.setTitle("Number Thing: Client");
            connection = createClient(menuClientIPTextField.getText(), Integer.parseInt(menuClientPortTextField.getText()));
            buttonDeact.set(true);
            try {
                connection.startConnection();
            } catch (Exception e1) {e1.printStackTrace(); buttonDeact.set(false);}
            while (!connection.available(0)) {

            }
            try {
                connection.send("SENDINITPLS");
            } catch (Exception e1) {e1.printStackTrace(); buttonDeact.set(false);}
        });

        radioButton1.fire();

        root.getChildren().addAll(menuBox);
        stage.setWidth(300);
        stage.setHeight(300);
    }

    /*
        ACTIONS:
        - CREATE-X (X*X Tiles)
        - SETNUMBER-X-Y-Z-1 (X: X-Coordinate, Y: Y-Coordinate, Z: Number, 1: 0 or 1)
        - SETTILE-X-Y-Z-M (X: X-Coordinate, Y: Y-Coordinate, Z: Number between 0 and 512 (Tile state in bin), M: Mark)
        - SETMARK-X-Y-Z (X: X-Coordinate, Y: Y-Coordinate, Z: boolean for mark)
     */

    private void sendTile(NumberBox nb) {
        try {
            connection.send("SETTILE-"+nb.getX()+"-"+nb.getY()+"-"+nb.getNumbersAsInt()+"-"+(nb.getMarked()?1:0));
        } catch (Exception e) {e.printStackTrace();}
    }

    private Server createServer(int port) {
        return new Server(port, data -> {
            Platform.runLater(() -> {
                String[] cut = data.toString().split("-");
                switch (cut[0]) {
                    case "LOAD": {
                        loadBox(Integer.parseInt(cut[1]));
                        try {
                            connection.send("LOAD-" + cut[1]);
                        } catch (Exception e) {
                            System.err.println("Error: Netzwerkfehler!");
                        }
                        break;
                    }
                    case "SETNUMBER": {
                        int tileID = Integer.parseInt(cut[1]) + Integer.parseInt(cut[2]) * tileNumber;
                        numberBoxList.get(tileID).setNumber(Integer.parseInt(cut[3]), Integer.parseInt(cut[4]) == 1);
                        sendTile(numberBoxList.get(tileID));
                        break;
                    }
                    case "SENDINITPLS": {
                        sendAll();
                        break;
                    }
                    case "SETTILE": {
                        int tileID = Integer.parseInt(cut[1]) + Integer.parseInt(cut[2]) * tileNumber;
                        numberBoxList.get(tileID).setNumbersFromInt(Integer.parseInt(cut[3]));
                        numberBoxList.get(tileID).setMarked(Integer.parseInt(cut[4]) == 1);
                        updateHighlight();
                        sendTile(numberBoxList.get(tileID));
                        break;
                    }
                    case "SETMARK": {
                        int tileID = Integer.parseInt(cut[1]) + Integer.parseInt(cut[2]) * tileNumber;
                        numberBoxList.get(tileID).setMarked(Integer.parseInt(cut[3]) == 1);
                        updateHighlight();
                        sendTile(numberBoxList.get(tileID));
                        break;
                    }
                    case "MEMBER": {
                        stage.setTitle("Number Thing: Server (" + Integer.parseInt(cut[1]) + " Verbindungen)");
                        break;
                    }
                }

                System.out.println("Data: " + data.toString());
            });
        });
    }

    private Client createClient(String ip, int port) {
        return new Client(ip, port, data -> {
            Platform.runLater(() -> {
                String[] cut = data.toString().split("-");
                switch (cut[0]) {
                    case "CREATE": {
                        loadBox(Integer.parseInt(cut[1]));
                        break;
                    }
                    case "LOAD": {
                        if(loading) {
                            loading = false;
                        } else {
                            loadBox(Integer.parseInt(cut[1]));
                        }
                    }
                    case "SETNUMBER": {
                        int tileID = Integer.parseInt(cut[1]) + Integer.parseInt(cut[2]) * tileNumber;
                        numberBoxList.get(tileID).setNumber(Integer.parseInt(cut[3]), Integer.parseInt(cut[4]) == 1);
                        break;
                    }
                    case "SETTILE": {
                        int tileID = Integer.parseInt(cut[1]) + Integer.parseInt(cut[2]) * tileNumber;
                        numberBoxList.get(tileID).setNumbersFromInt(Integer.parseInt(cut[3]));
                        numberBoxList.get(tileID).setMarked(Integer.parseInt(cut[4]) == 1);
                        updateHighlight();
                        break;
                    }
                    case "CONNECTIONLOST": {
                        stage.setTitle("Number Thing: Client (Verbindung verloren!)");
                        break;
                    }
                }

                System.out.println("Data: " + data.toString());
            });
        });
    }

    public void loadBox(int c) {
        System.out.println("Action: Erstelle Spielfeld der Größe " + c);
        tileNumber = c;
        root.getChildren().clear();
        numberBoxList.clear();

        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        if(isSudoku.get()) {
            vbox.setSpacing(10);
            int area = 0;
            for (int my = 0; my < 3; my++) {
                HBox hboxBig = new HBox();
                hboxBig.setAlignment(Pos.CENTER);
                hboxBig.setSpacing(10);
                for (int mx = 0; mx < 3; mx++) {
                    area++;
                    VBox vboxBig = new VBox();
                    vboxBig.setAlignment(Pos.CENTER);
                    for (int y = 0; y < 3; y++) {
                        HBox hbox = new HBox();
                        hbox.setAlignment(Pos.CENTER);
                        for (int x = 0; x < 3; x++) {
                            NumberBox nb = new NumberBox(this, mx*3+x, my*3+y, area);
                            hbox.getChildren().add(nb);
                            numberBoxList.add(nb);
                        }
                        vboxBig.getChildren().add(hbox);
                    }
                    hboxBig.getChildren().add(vboxBig);
                }
                vbox.getChildren().add(hboxBig);
            }
            Collections.sort(numberBoxList, (o1, o2) -> {
                    int comp = o1.getY()-o2.getY();
                    if(comp == 0) {
                        return o1.getX()-o2.getX();
                    }
                    return comp;
                }
            );
        } else {
            for (int y = 0; y < c; y++) {
                HBox hbox = new HBox();
                hbox.setAlignment(Pos.CENTER);
                for (int x = 0; x < c; x++) {
                    NumberBox nb = new NumberBox(this, x, y, 0);
                    hbox.getChildren().add(nb);
                    numberBoxList.add(nb);
                }
                vbox.getChildren().add(hbox);
            }
        }

        root.getChildren().add(vbox);

        stage.setWidth(55 * c + 10);
        stage.setHeight(75 * c + 35);
    }

    private void sendAll() {
        System.out.println("Action: sendAll");

        try {
            connection.send("CREATE-" + tileNumber);

            for (NumberBox nb : numberBoxList) {
                sendTile(nb);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void selectBox(NumberBox box) {
        if(selectedBox != null) {
            selectedBox.deselect();
        }
        selectedBox = box;
        selectedBox.select();
    }

    private void createListeners(Scene scene) {

        scene.setOnKeyPressed(keyEvent -> {
            KeyCode key = keyEvent.getCode();
            if(selectedBox != null) {
                switch (key) {
                    case NUMPAD0:
                    case SPACE:
                        switchMark(this.selectedBox);
                        break;
                    case NUMPAD1:
                        switchNumber(0);
                        break;
                    case NUMPAD2:
                        switchNumber(1);
                        break;
                    case NUMPAD3:
                        switchNumber(2);
                        break;
                    case NUMPAD4:
                        switchNumber(3);
                        break;
                    case NUMPAD5:
                        switchNumber(4);
                        break;
                    case NUMPAD6:
                        switchNumber(5);
                        break;
                    case NUMPAD7:
                        switchNumber(6);
                        break;
                    case NUMPAD8:
                        switchNumber(7);
                        break;
                    case NUMPAD9:
                        switchNumber(8);
                        break;
                    case DIGIT1:
                        if(hasNumpad.getValue()) {
                            highlightNumber(0);
                        } else {
                            switchNumber(0);
                        }
                        break;
                    case DIGIT2:
                        if(hasNumpad.getValue()) {
                            highlightNumber(1);
                        } else {
                            switchNumber(1);
                        }
                        break;
                    case DIGIT3:
                        if(hasNumpad.getValue()) {
                            highlightNumber(2);
                        } else {
                            switchNumber(2);
                        }
                        break;
                    case DIGIT4:
                        if(hasNumpad.getValue()) {
                            highlightNumber(3);
                        } else {
                            switchNumber(3);
                        }
                        break;
                    case DIGIT5:
                        if(hasNumpad.getValue()) {
                            highlightNumber(4);
                        } else {
                            switchNumber(4);
                        }
                        break;
                    case DIGIT6:
                        if(hasNumpad.getValue()) {
                            highlightNumber(5);
                        } else {
                            switchNumber(5);
                        }
                        break;
                    case DIGIT7:
                        if(hasNumpad.getValue()) {
                            highlightNumber(6);
                        } else {
                            switchNumber(6);
                        }
                        break;
                    case DIGIT8:
                        if(hasNumpad.getValue()) {
                            highlightNumber(7);
                        } else {
                            switchNumber(7);
                        }
                        break;
                    case DIGIT9:
                        if(hasNumpad.getValue()) {
                            highlightNumber(8);
                        } else {
                            switchNumber(8);
                        }
                        break;
                    case DIGIT0:
                        if(hasNumpad.getValue()) {
                            highlightNumber(-1);
                        }
                        break;
                    case Q:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(0);
                        }
                        break;
                    case W:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(1);
                        }
                        break;
                    case E:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(2);
                        }
                        break;
                    case R:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(3);
                        }
                        break;
                    case T:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(4);
                        }
                        break;
                    case Z:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(5);
                        }
                        break;
                    case U:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(6);
                        }
                        break;
                    case I:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(7);
                        }
                        break;
                    case O:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(8);
                        }
                        break;
                    case P:
                        if(hasNumpad.not().getValue()) {
                            highlightNumber(-1);
                        }
                        break;
                    case TAB: {
                        int tileId = getTileID(selectedBox, 0, 0) + 1;
                        selectBox(numberBoxList.get(tileId >= tileNumber * tileNumber ? 0 : tileId));
                        break;
                    }
                    case UP:
                        selectBox(numberBoxList.get(getTileID(selectedBox, 0, -1)));
                        break;
                    case DOWN:
                        selectBox(numberBoxList.get(getTileID(selectedBox, 0, 1)));
                        break;
                    case RIGHT:
                        selectBox(numberBoxList.get(getTileID(selectedBox, 1, 0)));
                        break;
                    case LEFT:
                        selectBox(numberBoxList.get(getTileID(selectedBox, -1, 0)));
                        break;
                }
            }
            switch (key) {
                case F1:
                    if (connection.isServer()) {
                        loadBox(1);
                        sendAll();
                    }
                    break;
                case F2:
                    if (connection.isServer()) {
                        loadBox(2);
                        sendAll();
                    }
                    break;
                case F3:
                    if (connection.isServer()) {
                        loadBox(3);
                        sendAll();
                    }
                    break;
                case F4:
                    if (connection.isServer()) {
                        loadBox(4);
                        sendAll();
                    }
                    break;
                case F5:
                    if (connection.isServer()) {
                        loadBox(5);
                        sendAll();
                    }
                    break;
                case F6:
                    if (connection.isServer()) {
                        loadBox(6);
                        sendAll();
                    }
                    break;
                case F7:
                    if (connection.isServer()) {
                        loadBox(7);
                        sendAll();
                    }
                    break;
                case F8:
                    if (connection.isServer()) {
                        loadBox(8);
                        sendAll();
                    }
                    break;
                case F9:
                    if (connection.isServer()) {
                        loadBox(9);
                        sendAll();
                    }
                    break;
                case S: {
                    File file = fileChooser.showSaveDialog(stage);

                    if (file != null) {
                        System.out.println("Action: Speichere in:" + file.getAbsolutePath());
                        save(file);
                        fileChooser.setInitialDirectory(file.getParentFile());
                    } else {
                        System.out.println("Action: Speichervorgang abgebrochen!");
                    }
                    break;
                }
                case L: {
                    File file = fileChooser.showOpenDialog(stage);

                    if (file != null) {
                        System.out.println("Action: Lade aus: " + file.getAbsolutePath());
                        load(file);
                        fileChooser.setInitialDirectory(file.getParentFile());
                    } else {
                        System.out.println("Action: Ladevorgang abgebrochen!");
                    }
                    break;
                }
                case O: {
                    //Fill all empty cells with all 9 digits.
                    for(NumberBox nb: numberBoxList) {
                        if(!nb.getMarked() && nb.getNumbersAsInt()==0) {
                            nb.setNumbersFromInt(511);
                        }
                    }
                    break;
                }
                case P: {
                    //Remove all red small red numbers for all 9 digits.
                    boolean end;
                    do {
                        end = true;
                        for (NumberBox nb : numberBoxList) {
                            if(nb.removeIfRed()) end = false;
                        }
                    } while(!end);
                    break;
                }
            }
        });
    }

    public void save(File file) {
        Writer fw = null;

        try {
            fw = new FileWriter(file);

            fw.write("gr:" + tileNumber);
            fw.append(System.getProperty("line.separator")); // e.g. "\n"

            for(NumberBox nb: numberBoxList) {
                fw.write("tile:" + getTileID(nb, 0, 0) + ":" + nb.getNumbersAsInt() + ":" + (nb.getMarked()?1:0));
                fw.append(System.getProperty("line.separator"));
            }
        } catch ( IOException e ) {
            System.err.println( "Error: Konnte Datei nicht erstellen" );
        } finally {
            if ( fw != null )
                try { fw.close(); } catch ( IOException e ) { e.printStackTrace(); }
        }
    }

    public void load(File file) {
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));

            String line;
            while ((line = reader.readLine()) != null) {
                String[] cut = line.split(":");
                if(cut[0].equals("gr")) {
                    loadBox(Integer.parseInt(cut[1]));
                    try {
                        connection.send("LOAD-" + cut[1]);
                    } catch (Exception ne) {
                        System.err.println("Error: Netzwerkfehler!");
                    }
                } else if(cut[0].equals("tile")) {
                    NumberBox nb = numberBoxList.get(Integer.parseInt(cut[1]));
                    nb.setNumbersFromInt(Integer.parseInt(cut[2]));
                    nb.setMarked(Integer.parseInt(cut[3])==1);
                    sendTile(nb);
                } else {
                    System.err.println("Error: Fehlerhafte Speicherdatei!");
                    break;
                }
            }
        } catch ( IOException e ) {
            System.err.println( "Error: Fehler beim Lesen der Datei!" );
        } finally {
            if(reader != null)
                try { reader.close(); } catch ( Exception e ) { }
        }
    }

    public int getTileID(NumberBox nb, int divX, int divY) {
        if(nb.getX() + divX < 0 || nb.getX() + divX >= tileNumber) divX = 0;
        if(nb.getY() + divY < 0 || nb.getY() + divY >= tileNumber) divY = 0;
        return nb.getX() + divX + (nb.getY() + divY) * tileNumber;
    }

    public void switchNumber(int number) {
        boolean val = selectedBox.getNumbers()[number];
        selectedBox.setNumber(number, !val);
        sendTile(selectedBox);
    }

    public void highlightNumber(int number) {
        for(NumberBox box: numberBoxList) {
            box.highlightNumber(number);
        }
    }

    public void switchMark(NumberBox nb) {
        nb.switchMark();
        markSet(nb);
    }

    public void markSet(NumberBox nb) {
        try {
            sendTile(nb);
            updateHighlight();
        } catch (Exception e) {e.printStackTrace();}
    }

    public void updateHighlight() {
        for(NumberBox box: numberBoxList) {
            box.highlightChange();
        }
    }

    public int getTileNumber() {
        return tileNumber;
    }

    public boolean isSudoku() {
        return isSudoku.get();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

