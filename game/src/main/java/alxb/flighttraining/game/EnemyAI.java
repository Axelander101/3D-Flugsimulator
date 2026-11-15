package alxb.flighttraining.game;

public class EnemyAI {
    private FlightCommandAi input;
    private FlightPhysics physics;


    public EnemyAI(FlightCommandAi input) {
        this.input = input;
    }

    public void update(){
        input.setThrottle(1);

    }
}
