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
    private Worm[] wormsData;

    public Bot(Random random, GameState gameState) {
        this.random = random;
        this.gameState = gameState;
        this.opponent = gameState.opponents[0];
        this.currentWorm = getCurrentWorm(gameState);
        this.wormsData = gameState.myPlayer.worms;
    }

    private MyWorm getCurrentWorm(GameState gameState) {
        return Arrays.stream(gameState.myPlayer.worms)
                .filter(myWorm -> myWorm.id == gameState.currentWormId)
                .findFirst()
                .get();
    }

    public Command run() {

        Worm enemyWorm = getFirstWormInRange();
        Utilities utilities = new Utilities();

        if (enemyWorm != null) {
            Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);

            if(enemyWorm.id == opponent.currentWormId && enemyWorm.roundsUntilUnfrozen==0){
                return EscapeShootStrategy(enemyWorm);
            } else{
                return AttackStrategy(enemyWorm);
            }
        }

        Cell destBlock = getNearestPowerup(currentWorm.position.x, currentWorm.position.y);
        if(destBlock == null) {
            destBlock = getNearestEnemy(currentWorm.position.x, currentWorm.position.y);
        }
        return moveToObjective(currentWorm.position.x, currentWorm.position.y, destBlock);
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
        List<CellDistance> distanceList = new ArrayList<CellDistance>();

        for(int i = 0; i < surroundingBlocks.size(); i++) {
            CellDistance d = new CellDistance(surroundingBlocks.get(i), euclideanDistance(surroundingBlocks.get(i).x,surroundingBlocks.get(i).y, destX, destY));
            distanceList.add(d);
        }

        List<CellDistance> sortedDistance = distanceList.stream()
                .sorted(Comparator.comparing(CellDistance::getDistance))
                .collect(Collectors.toList());

        return sortedDistance.get(0).cell;
    }

    private int calculateTurnToDest(int initialX, int initialY, int destX, int destY) {
        int total = 0;
        List<Cell> surroundingBlocks = getSurroundingCells(initialX, initialY);
        Cell block = getNearestPath(surroundingBlocks, destX, destY);

        while(block.x != destX || block.y != destY) {
            if(block.type == CellType.DIRT) {
                total += 2;
            } else if(block.type == CellType.AIR) {
                total += 1;
            }
            surroundingBlocks = getSurroundingCells(block.x, block.y);
            block = getNearestPath(surroundingBlocks, destX, destY);
        }
        return total;
    }

    private Cell getNearestEnemy(int currPosX, int currPosY) {
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].occupier != null && gameState.map[j][i].occupier.playerId == opponent.id) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        List<CellTurn> objectivesDistance = new ArrayList<CellTurn>();
        for(int i = 0; i < objectives.size(); i++) {
            CellTurn t = new CellTurn(objectives.get(i), calculateTurnToDest(currPosX, currPosY, objectives.get(i).x, objectives.get(i).y));
            objectivesDistance.add(t);
        }

        List<CellTurn> sortedObjectives = objectivesDistance.stream()
                .sorted(Comparator.comparing(CellTurn::getTurns))
                .collect(Collectors.toList());

        return sortedObjectives.get(0).cell;
    }

    private Cell getNearestPowerup(int currPosX, int currPosY) {
        List<Cell> objectives = new ArrayList<Cell>();

        for(int i = 0; i < 33; i++) {
            for(int j = 0; j < 33; j++) {
                if(gameState.map[j][i].powerup != null) {
                    objectives.add(gameState.map[j][i]);
                }
            }
        }

        if(objectives.size() == 0) {return null;}

        List<CellTurn> objectivesDistance = new ArrayList<CellTurn>();
        for(int i = 0; i < objectives.size(); i++) {
            CellTurn t = new CellTurn(objectives.get(i), calculateTurnToDest(currPosX, currPosY, objectives.get(i).x, objectives.get(i).y));
            objectivesDistance.add(t);
        }

        List<CellTurn> sortedObjectives = objectivesDistance.stream()
                .sorted(Comparator.comparing(CellTurn::getTurns))
                .collect(Collectors.toList());

        return sortedObjectives.get(0).cell;

    }

    private Command moveToObjective(int initialX, int initialY, Cell destination) {
        List<Cell> surroundingBlocks = getSurroundingCells(initialX, initialY);
        Cell chosenBlock = getNearestPath(surroundingBlocks, destination.x, destination.y);

        if (chosenBlock.type == CellType.AIR) {
            return new MoveCommand(chosenBlock.x, chosenBlock.y);
        } else if (chosenBlock.type == CellType.DIRT) {
            return new DigCommand(chosenBlock.x, chosenBlock.y);
        } else if (chosenBlock.type==CellType.LAVA) {
            return EscapeLavaStrategy();
        }

        return new DoNothingCommand();
    }

    private class CellDistance {
        Cell cell;
        int distance;

        public int getDistance() {
            return distance;
        }

        CellDistance(Cell cell, int distance){
            this.cell = cell;
            this.distance = distance;
        }
    }

    private class CellTurn {
        Cell cell;
        int turns;

        public int getTurns() {return turns;}

        CellTurn(Cell cell, int turns) {
            this.cell = cell;
            this.turns = turns;
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

    private Command EscapeLavaStrategy(){
        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x<33/2 ? ++x : --x;
        int moveY = y<33/2 ? ++y : --y;
        return new MoveCommand(moveX,moveY);
    }

    private Command avoidFriendlyFire(Worm enemyWorm) {
        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int enemyX = enemyWorm.position.x;
        int enemyY = enemyWorm.position.y;

        for(int i = x - 1; i <= x + 1; i++){
            if(x == enemyX && i == x){
                continue;
            }

            for(int j = y - 1; j <= y + 1; j++){
                if(y == enemyY && j == y) {
                    continue;
                }

                if(gameState.map[j][i].type == CellType.AIR){
                    return new MoveCommand(i, j);
                }
            }
        }

        return AttackAnotherWorm(enemyWorm);
    }

    private Command AttackAnotherWorm(Worm enemyWorm) {
        Utilities utilities = new Utilities();
        for(Worm anotherEnemyWorm : opponent.worms){
            if(anotherEnemyWorm.id != enemyWorm.id){
                if(utilities.gradient(currentWorm, enemyWorm) != utilities.gradient(currentWorm, anotherEnemyWorm)){
                    Direction direction = resolveDirection(currentWorm.position, anotherEnemyWorm.position);
                    return new ShootCommand(direction);
                }
            }
        }

        return EscapeLavaStrategy();
    }

    private Command EscapeShootStrategy(Worm enemyWorm){

        Utilities utilities = new Utilities();

        int x = currentWorm.position.x;
        int y = currentWorm.position.y;
        int moveX = x;
        int moveY = y;
        boolean conflict = false;

        if(currentWorm.position.x==enemyWorm.position.x){
            System.out.println("Escape shoot strategy alternatif 1");
            moveX = x<33/2 ? x+1 : x-1;
            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);

            if(conflict){ //Jika conflict coba jalan lain terlebih dahulu
                moveX = x<33/2 ? x-1: x+1;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveX = x<33/2? x+1 : x-1;
                    moveY = y<33/2 ? y+1 : y-1;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveX = x<33/2 ? x+1 : x-1;
                        moveY = y<33/2 ? y-1 : y+1;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            moveX = x < 33/2 ? x-1 : x+1;
                            moveY = y < 33/2 ? y+1 : y-1;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if(conflict){
                                moveX = x < 33/2 ? x-1 : x+1;
                                moveY = y < 33/2 ? y-1 : y+1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if(conflict){
                                    return AttackStrategy(enemyWorm);
                                }
                            }
                        }
                    }
                }
            }

        } else if(currentWorm.position.y==enemyWorm.position.y){
            System.out.println("Escape shoot strategy alternatif 2");
            moveY = y<33/2 ? y+1 : y-1;
            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);

            if(conflict) { //Jika conflict coba jalan lain terlebih dahulu
                moveY = y < 33 / 2 ? y-1 : y+1;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if (conflict) {
                    moveY = y < 33 / 2 ? y+1 : y-1;
                    moveX = y < 33 / 2 ? x+1 : x-1;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if (conflict) {
                        moveY = y < 33 / 2 ? y+1 : y-1;
                        moveX = x < 33 / 2 ? x-1 : x+1;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if (conflict) {
                            moveY = y < 33 / 2 ? y-1 : y+1;
                            moveX = x < 33 / 2 ? x+1: x-1;
                            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                            if (conflict) {
                                moveY = y < 33 / 2 ? y-1 : y+1;
                                moveX = x < 33 / 2 ? x-1 : x+1;
                                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                                if (conflict) {
                                    return AttackStrategy(enemyWorm);
                                }
                            }
                        }
                    }
                }
            }
        } else { // currentWorm.position.x != enemyWorm.position.x && currentWorm.position.y != enemyWorm.position.y
            System.out.println("Escape shoot strategy alternatif 3");
            moveX = x<33/2? x+1 : x-1;
            moveY = y<33/2 ? y+1 :y-1;
            conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
            if(conflict){
                moveX = x<33/2 ? x+1 : x-1;
                moveY = y<33/2 ? y-1 : y+1;
                conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                if(conflict){
                    moveX = x < 33/2 ? x-1 : x+1;
                    moveY = y < 33/2 ? y+1 : y-1;
                    conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                    if(conflict){
                        moveX = x < 33/2 ? x-1 : x+1;
                        moveY = y < 33/2 ? y-1 : y+1;
                        conflict = utilities.isPathInvalid(enemyWorm,currentWorm,opponent,moveX,moveY,gameState);
                        if(conflict){
                            return AttackStrategy(enemyWorm);
                        }
                    }
                }
            }
        }

        System.out.println("Avoid shoot strategy executed!");

        return new MoveCommand(moveX,moveY);
    }

    private Command AttackStrategy(Worm enemyWorm) {
        // First check can use bomb or not
        Direction direction = resolveDirection(currentWorm.position, enemyWorm.position);
        if (currentWorm.id == 2) {
            if (currentWorm.bananaBombs.count > 0) {
                return BananaBombStrategy(enemyWorm, direction);
            }
        } else if (currentWorm.id == 3) {
            if (currentWorm.snowballs.count > 0 && enemyWorm.roundsUntilUnfrozen == 0) {
                return SnowballStrategy(enemyWorm, direction);
            }
        }
        return ShootStrategy(direction,enemyWorm);
    }

    private Command ShootStrategy(Direction direction,Worm enemyWorm){
        Utilities utilities = new Utilities();
        boolean canShoot = true;
        for (Worm anotherWorm : this.wormsData) {
            if (currentWorm.id != anotherWorm.id && anotherWorm.health > 0) {
                // Check gradiennya gimana
                if (utilities.gradient(currentWorm, anotherWorm) == utilities.gradient(currentWorm, enemyWorm)) {
                    canShoot = false;
                    break;
                }
            }
        }
        System.out.println("Shoot strategy executed!");
        System.out.println(canShoot);

        return canShoot ? new ShootCommand(direction) : avoidFriendlyFire(enemyWorm);
    }

    private Command BananaBombStrategy(Worm enemyWorm, Direction direction){
        for(int i=0;i<5;i++){
            if(i<3){
                for(int j=2-i;j<3+i;j++){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[1].position.x && y==gameState.myPlayer.worms[1].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){
                        System.out.println("ANJING");
                        return new ShootCommand(direction);
                    }
                }
            } else{
                for(int j=i-2;j<7-i;j++ ){
                    int x = enemyWorm.position.x - (2-i);
                    int y = enemyWorm.position.y - (2-j);
                    if((x==gameState.myPlayer.worms[0].position.x && y==gameState.myPlayer.worms[0].position.y) || (x==gameState.myPlayer.worms[1].position.x && y==gameState.myPlayer.worms[1].position.y) || (x==gameState.myPlayer.worms[2].position.x && y==gameState.myPlayer.worms[2].position.y)){
                        System.out.println("ANJING2");
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
