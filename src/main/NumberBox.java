package main;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

class NumberBox extends StackPane {

    private BooleanProperty[] numbers;
    private Label[] text;
    private Label bigText;
    private BooleanProperty selected;
    private BooleanProperty marked;
    private IntegerProperty highlightNumber;
    private Main main;

    private int x, y, area;

    public NumberBox(Main main, int x, int y, int area) {
        this.x = x;
        this.y = y;
        this.area = area;
        this.main = main;
        numbers = new SimpleBooleanProperty[9];
        for(int i = 0; i < 9; i++) {
            numbers[i] = new SimpleBooleanProperty(false);
        }
        selected = new SimpleBooleanProperty(false);
        marked = new SimpleBooleanProperty(false);
        highlightNumber = new SimpleIntegerProperty(9);
        highlightNumber.addListener(e -> highlightChange());

        text = new Label[9];
        VBox vbox = new VBox();
        for(int i = 0; i < 9; i++) {
            text[i] = new Label(Integer.toString(i + 1));
            text[i].visibleProperty().bind(numbers[i].and(marked.not()));
            text[i].getStyleClass().add("smallNumber");
        }
        HBox hbox123 = new HBox(5, text[0], text[1], text[2]);
        HBox hbox456 = new HBox(5, text[3], text[4], text[5]);
        HBox hbox789 = new HBox(5, text[6], text[7], text[8]);
        vbox.getChildren().addAll(hbox789, hbox456, hbox123);

        bigText = new Label();
        bigText.visibleProperty().bind(marked);
        bigText.getStyleClass().add("bigNumber");
        getChildren().addAll(vbox, bigText);
        getStyleClass().add("numberBox");

        this.setOnMouseClicked(mouseEvent -> {
            MouseButton button = mouseEvent.getButton();
            if(button == MouseButton.PRIMARY){
                main.selectBox(this);
                if(getMarked()) {
                    main.highlightNumber(Integer.parseInt(bigText.getText())-1);
                }
            } else if(button == MouseButton.SECONDARY) {
                switchMark();
                main.markSet(this);
            }
        });
        deselect();
    }

    public void select() {
        selected.set(true);
        if(!getStyleClass().contains("selected"))
            getStyleClass().add("selected");
    }

    public void deselect() {
        selected.set(false);
        if(getStyleClass().contains("selected"))
            getStyleClass().remove("selected");
    }

    public void highlightNumber(int number) {
        highlightNumber.setValue(number);
    }

    public void highlightChange() {
        for(int i = 0; i < 9; i++)
            if(text[i].getStyleClass().contains("highlight"))
                text[i].getStyleClass().remove("highlight");
            else if(text[i].getStyleClass().contains("highlight-red"))
                text[i].getStyleClass().remove("highlight-red");

        if(bigText.getStyleClass().contains("highlight"))
            bigText.getStyleClass().remove("highlight");
        else if(bigText.getStyleClass().contains("highlight-red"));


        int number = highlightNumber.intValue();
        if(number >= 0 && number <= 8) {

            if(isHighlightRed())
                text[number].getStyleClass().add("highlight-red");
            else
                text[number].getStyleClass().add("highlight");

            if(bigText.getText() != "" && Integer.parseInt(bigText.getText())-1 == number)
                bigText.getStyleClass().add("highlight");
        }
    }

    public boolean removeIfRed() {
        boolean bool = false;
        for(int number = 0; number < 9; number++) {
            int hn = highlightNumber.get();
            highlightNumber.set(number);
            if(!getMarked() && isHighlightRed()) {
                setNumber(number, false);
                if(!getMarked()) {
                    setMarked(true);
                    if(getMarked()) {
                        bool = true;
                    }
                }
            }
            highlightNumber.set(hn);
        }
        return bool;
    }

    private boolean isHighlightRed() {
        int tn = main.getTileNumber();
        for(int i = 0; i < tn; i++) {
            NumberBox box = main.numberBoxList.get(x + i * tn);
            if(box.getMarked() && box.getNumbers()[highlightNumber.get()]) {
                return true;
            }
        }
        for(int i = 0; i < tn; i++) {
            NumberBox box = main.numberBoxList.get(i + y * tn);
            if(box.getMarked() && box.getNumbers()[highlightNumber.get()]) {
                return true;
            }
        }
        if(main.isSudoku()) {
            for (NumberBox box : main.numberBoxList) {
                if (box.getMarked() && box.getArea() == area && box.getNumbers()[highlightNumber.get()]) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setNumber(int number, boolean val) {
        if(!marked.getValue()) {
            numbers[number].set(val);
        }
    }

    public void switchNumber(int number) {
        if(!marked.getValue())
            numbers[number].set(!numbers[number].getValue());
    }

    public void clear() {
        if(!marked.getValue())
            for(int i = 0; i < 9; i++) {
                numbers[i].set(false);
            }
    }

    public void setNumbers(boolean[] vals) {
        for(int i = 0; i < 9; i++) {
            numbers[i].set(vals[i]);
        }
    }

    public void setNumbersFromInt(int val) {
        boolean[] vals = new boolean[9];
        for(int i = 0; i < 9; i++, val /= 2) {
            vals[i] = val % 2 == 1;
        }
        setNumbers(vals);
    }

    public boolean[] getNumbers() {
        boolean[] vals = new boolean[9];
        for(int i = 0; i < 9; i++) {
            vals[i] = numbers[i].get();
        }
        return vals;
    }

    public int getNumbersAsInt() {
        int val = 0;
        for(int i = 0, a = 1; i < 9; i++, a*=2) {
            val += numbers[i].get()?a:0;
        }
        return val;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void switchMark() {
        if(getMarked()) {
            setMarked(false);
        } else {
            setMarked(true);
        }
    }

    public void setMarked(boolean val) {
        if(val) {
            int numberCount = 0;
            int number = 0;
            for(int i = 0; i < 9; i++) {
                if(numbers[i].get()) {
                    numberCount++;
                    number = i + 1;
                }
            }
            if(numberCount == 1) {
                if(bigText.getStyleClass().contains("highlight"))
                    bigText.getStyleClass().remove("highlight");

                marked.set(val);
                bigText.setText(Integer.toString(number));

                if(text[number-1].getStyleClass().contains("highlight") && !bigText.getStyleClass().contains("highlight"))
                    bigText.getStyleClass().add("highlight");

                if (!getStyleClass().contains("marked"))
                    getStyleClass().add("marked");
            }
        } else {
            marked.set(val);
            if(getStyleClass().contains("marked"))
                getStyleClass().remove("marked");
        }
    }

    public boolean getMarked() {
        return marked.get();
    }

    public int getArea() {
        return area;
    }
}
