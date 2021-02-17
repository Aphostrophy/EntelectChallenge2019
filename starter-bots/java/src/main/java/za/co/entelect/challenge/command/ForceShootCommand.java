package za.co.entelect.challenge.command;

import za.co.entelect.challenge.enums.Direction;

public class ForceShootCommand implements Command {

    private Direction direction;
    private int id;

    public ForceShootCommand(Direction direction, int id) {
        this.direction = direction;
        this.id = id;
    }

    @Override
    public String render() {
        return String.format("select %d ;shoot %s", id,direction.name());
    }
}
