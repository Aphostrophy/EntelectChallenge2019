package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.CellType;
import za.co.entelect.challenge.enums.Direction;

import java.util.*;
import java.util.stream.*;

public class Bot {

    private Random random;
    private GameState gameState;
    private Opponent opponent;
    private MyWorm currentWorm;

    public Bot(Random random, GameState gameState) {
        this.random = random;
        this.gameState = gameState;
        this.opponent = gameState.opponents[0];
        this.currentWorm = getCurrentWorm(gameState);
    }

    private MyWorm getCurrentWorm(GameState gameState) {
        return Arrays.stream(gameState.myPlayer.worms)
                .filter(myWorm -> myWorm.id == gameState.currentWormId)
                .findFirst()
                .get();
    }

//    private int getOpponentWorm(GameState gameState) {
//        return Arrays.stream(gameState.myPlayer.worms)
//                .filter(myWorm -> myWorm.id == gameState.currentWormId)
//                .findFirst()
//                .get();
//    }

    public Command run() {

        Worm enemyWorm = getFirstWormInRange();
        Utilities utilities = new Utilities();
        if (enemyWorm != null) {
            Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
//            System.out.println(enemyWorm.id);
//            System.out.println(opponent.currentWormId);
            if(enemyWorm.id == opponent.currentWormId && enemyWorm.roundsUntilUnfrozen==0){
                return EscapeShootStrategy(enemyWorm);
            } else{
                return AttackStrategy(enemyWorm);
            }
        }

        List<Cell> surroundingBlocks = getSurroundingCells(currentWorm.position.x, currentWorm.position.y);
        int cellIdx = utilities.getDirtID(surroundingBlocks);

        if(cellIdx == -1){
            return EscapeLavaStrategy(surroundingBlocks);
        } else{
            Cell block = surroundingBlocks.get(cellIdx);
            if (block.type == CellType.AIR) {
                return new MoveCommand(block.x, block.y);
            } else if (block.type == CellType.DIRT) {
                return new DigCommand(block.x, block.y);
            } else if (block.type==CellType.LAVA) {
                return EscapeLavaStrategy(surroundingBlocks);
            }
        }

        return new DoNothingCommand();
    }

    private Worm getFirstWormInRange() {

        Set<String> cells = constructFireDirectionLines(currentWorm.weapon.range)
                .stream()
                .flatMap(Collection::stream)
                .map(cell -> String.format("%d_%d", cell.x, cell.y))
                .collect(Collectors.toSet());

        for (Worm enemyWorm : opponent.worms) {
            if(enemyWorm.health>0){
                String enemyPosition = String.format("%d_%d", enemyWorm.position.x, enemyWorm.position.y);
                if (cells.contains(enemyPosition)) {
                    return enemyWorm;
                }
            }
        }

        return null;
    }

    private List<List<Cell>> constructFireDirectionLines(int range) {
        List<List<Cell>> directionLines = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            List<Cell> directionLine = new ArrayList<>();
            for (int directionMultiplier = 1; directionMultiplier <= range; directionMultiplier++) {

                int coordinateX = currentWorm.position.x + (directionMultiplier * direction.x);
                int coordinateY = currentWorm.position.y + (directionMultiplier * direction.y);

                if (!isValidCoordinate(coordinateX, coordinateY)) {
                    break;
                }

                if (euclideanDistance(currentWorm.position.x, currentWorm.position.y, coordinateX, coordinateY) > range) {
                    break;
                }

                Cell cell = gameState.map[coordinateY][coordinateX];
                if (cell.type != CellType.AIR) {
                    break;
                }

                directionLine.add(cell);
            }
            directionLines.add(directionLine);
        }

        return directionLines;
    }

    private List<Cell> getSurroundingCells(int x, int y) {
        ArrayList<Cell> cells = new ArrayList<>();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                // Don't include the current position
                if ((i != x || j != y) && isValidCoordinate(i, j) && gameState.map[j][i].type!=CellType.DEEP_SPACE) {
                    cells.add(gameState.map[j][i]);
                }
            }
        }

        return cells;
    }

    private Cell getNearestPath(List<Cell> surroundingBlocks, int destX, int destY) {
        List<Test> newList = new ArrayList<Test>();
        for(int i = 0; i < surroundingBlocks.size(); i++) {
            Test foo = new Test(surroundingBlocks.get(i), euclideanDistance(surroundingBlocks.get(i).x,surroundingBlocks.get(i).y, destX, destY));
            newList.add(foo);
        }
        List<Test> sortedList = newList.stream()
                .sorted(Comparator.comparing(Test::getDistance))
                .collect(Collectors.toList());
        for(int i = 0; i < sortedList.size(); i++) {
            System.out.printf("%d %d %d\n", sortedList.get(i).cell.x, sortedList.get(i).cell.y, sortedList.get(i).distance);
        }
        return sortedList.get(0).cell;
    }

    private class Test {
        Cell cell;
        int distance;

        public int getDistance() {
            return distance;
        }

        Test(Cell cell, int distance){
            this.cell = cell;
            this.distance = distance;
        }
    }

    private int euclideanDistance(int aX, int aY, int bX, int bY) {
        return (int) (Math.sqrt(Math.pow(aX - bX, 2) + Math.pow(aY - bY, 2)));
    }

    private boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < gameState.mapSize
                && y >= 0 && y < gameState.mapSize;
    }

    private Direction resolveDirection(Position a, Position b) {
        StringBuilder builder = new StringBuilder();

        int verticalComponent = b.y - a.y;
        int horizontalComponent = b.x - a.x;

        if (verticalComponent < 0) {
            builder.append('N');
        } else if (verticalComponent > 0) {
            builder.append('S');
        }

        if (horizontalComponent < 0) {
            builder.append('W');
        } else if (horizontalComponent > 0) {
            builder.append('E');
        }

        return Direction.valueOf(builder.toString());
    }

    private Command EscapeLavaStrategy(List<Cell> surroundingBlocks){
        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x<33/2 ? ++x : --x;
        int moveY = y<33/2 ? ++y : --y;
        return new MoveCommand(moveX,moveY);
    }

    private Command EscapeShootStrategy(Worm enemyWorm){
        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x;
        int moveY = y;
        boolean conflict = false;

        if(currentWorm.position.x==enemyWorm.position.x){
            moveX = x<33/2 ? ++x : --x;
            for (Worm cacingMusuh : opponent.worms) {
                if (cacingMusuh.position.x == moveX  && cacingMusuh.position.y ==moveY &&cacingMusuh.health>0) {
                    conflict=true;
                }
            }
            if(conflict){
                return AttackStrategy(enemyWorm);
            }

        } else if(currentWorm.position.y==enemyWorm.position.y){
            moveY = y<33/2 ? ++y : --y;
            for (Worm cacingMusuh : opponent.worms) {
                if (cacingMusuh.position.x == moveX  && cacingMusuh.position.y ==moveY) {
                    conflict=true;
                }
            }
            if(conflict){
                return AttackStrategy(enemyWorm);
            }
        } else { // currentWorm.position.x != enemyWorm.position.x && currentWorm.position.y != enemyWorm.position.y
            moveX = x<33/2 ? ++x : --x;
            moveY = y<33/2 ? ++y : --y;
            for (Worm cacingMusuh : opponent.worms) {
                if (cacingMusuh.position.x == moveX  && cacingMusuh.position.y ==moveY) {
                    conflict=true;
                }
            }
            if(conflict){
                return AttackStrategy(enemyWorm);
            }
        }

        return new MoveCommand(moveX,moveY);
    }

    private Command AttackStrategy(Worm enemyWorm) {
        // First check can use bomb or not
        Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
        if(currentWorm.id == 2) {
            if(currentWorm.bananaBombs.count > 0) {
//                System.out.println("Bisa ngebom");
//                System.out.println("Posisi gw : " + currentWorm.position.x + " " + currentWorm.position.y);
//                System.out.println("Posisi musuh terdekat : " + enemyWorm.position.x + " " + enemyWorm.position.y);
                return BananaBombStrategy(enemyWorm,direction);
            }
            System.out.println("gabisa ngebom");
        }if(currentWorm.id == 3) {
            if(currentWorm.snowballs.count > 0 && enemyWorm.roundsUntilUnfrozen==0) {
                System.out.println("Bisa ngefreeze");
                return SnowballStrategy(enemyWorm,direction);
            }
        }
        return new ShootCommand(direction);
    }

//    private Command ShootStrategy(Direction direction,Worm enemyWorm){
//
//    }

    private Command BananaBombStrategy(Worm enemyWorm, Direction direction){
//        for(Worm w : gameState.myPlayer.worms){
//            for(int i=0;i<5;i++){
//                if(i<3){
//                    for(int j=2-i;j<3+i;j++){
//                        int x = w.position.x - (2-i);
//                        int y = w.position.y - (2-j);
//                        System.out.println(x+ ":" + y);
//                    }
//                } else{
//                    for(int j=i-2;j<7-i;j++ ){
//                        int x = w.position.x - (2-i);
//                        int y = w.position.y - (2-j);
//                        System.out.println(x+ ":" + y);
//                    }
//                }
//            }
//        }
        for(int i=0;i<5;i++){
            if(i<3){
                for(int j=2-i;j<3+i;j++){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[1].position.x && y==gameState.myPlayer.worms[1].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){
                        return new ShootCommand(direction);
                    }
                }
            } else{
                for(int j=i-2;j<7-i;j++ ){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[1].position.x && y==gameState.myPlayer.worms[1].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){
                        return new ShootCommand(direction);
                    }
                }
            }
        }
        return new BombCommand(enemyWorm.position.x, enemyWorm.position.y);
    }

    private Command SnowballStrategy(Worm enemyWorm, Direction direction){
        for(Worm w : gameState.myPlayer.worms){
            int x = w.position.x;
            int y = w.position.y;
            for(int i=enemyWorm.position.x-1;i<enemyWorm.position.x+1;i++){
                for(int j=enemyWorm.position.y-1;j<enemyWorm.position.y+1;j++){
                    if(i==x && j==y){
                        return new ShootCommand(direction);
                    }
                }
            }
        }
        return new SnowballCommand(enemyWorm.position.x, enemyWorm.position.y);
    }
}
