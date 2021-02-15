package za.co.entelect.challenge.command;

import za.co.entelect.challenge.enums.Direction;

public class BombCommand implements Command {

    private int x;
    private int y;

    public BombCommand(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String render() {
        return String.format("banana %d %d", this.x, this.y);
    }
}
