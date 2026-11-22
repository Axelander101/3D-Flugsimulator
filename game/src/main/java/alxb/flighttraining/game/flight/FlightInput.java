package alxb.flighttraining.game.flight;

import com.jme3.input.InputManager;
import com.jme3.input.Joystick;
import com.jme3.input.JoystickAxis;
import com.jme3.input.JoystickConnectionListener;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.JoyAxisTrigger;
import com.jme3.input.controls.KeyTrigger;

public final class FlightInput {
    private final InputManager inputManager;

    // Controller
    public float rollInput = 0f;
    public float yawInput = 0f;
    public float pitchInput = 0f;

    public float throttleUpInput = 0f;
    public float throttleDownInput = 0f;

    // Tastatur
    public boolean pitchUp;
    public boolean pitchDown;
    public boolean rollLeft;
    public boolean rollRight;
    public boolean yawLeft;
    public boolean yawRight;

    public boolean throttleUp;
    public boolean throttleDown;

    public boolean wheelBrake;
    private boolean flightControlsEnabled;
    private int systemCommands;
    public void setFlightControlsEnabled(boolean enabled) {
        flightControlsEnabled=enabled;
        if(!enabled){systemCommands=0;wheelBrake=false;}
    }
    public int drainSystemCommands(){int value=systemCommands;systemCommands=0;return value;}

    public boolean shoot;
    public boolean infraredMissile;

    public FlightInput(InputManager inputManager) {
        this.inputManager = inputManager;
        configureJoystick();
        configureKeyboard();
        String[] names={"AircraftGear","AircraftAirbrake","AircraftFlaps","AircraftParkingBrake",
                "AircraftCanopy","AircraftHook","AircraftFold","AircraftEngine","AircraftDisplayPage","AircraftDisplayDetail","AircraftWheelBrake"};
        int[] keys={KeyInput.KEY_G,KeyInput.KEY_X,KeyInput.KEY_L,KeyInput.KEY_P,
                KeyInput.KEY_C,KeyInput.KEY_H,KeyInput.KEY_K,KeyInput.KEY_I,KeyInput.KEY_V,KeyInput.KEY_TAB,KeyInput.KEY_B};
        for(int i=0;i<names.length;i++) inputManager.addMapping(names[i],new KeyTrigger(keys[i]));
        ActionListener systems=(name,pressed,tpf)->{
            if(name.equals("AircraftWheelBrake")){wheelBrake=pressed && flightControlsEnabled;return;}
            if(!pressed || !flightControlsEnabled)return;
            for(int i=0;i<10;i++)if(names[i].equals(name)){systemCommands|=1<<i;break;}
        };
        inputManager.addListener(systems,names);
    }

    private void configureKeyboard() {
        inputManager.addMapping("PitchUp", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("PitchDown", new KeyTrigger(KeyInput.KEY_S));

        inputManager.addMapping("RollLeft", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("RollRight", new KeyTrigger(KeyInput.KEY_D));

        inputManager.addMapping("YawLeft", new KeyTrigger(KeyInput.KEY_Q));
        inputManager.addMapping("YawRight", new KeyTrigger(KeyInput.KEY_E));

        inputManager.addMapping("ThrottleUp", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("ThrottleDown", new KeyTrigger(KeyInput.KEY_F));


        inputManager.addMapping("Shoot", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("InfraredMissile", new KeyTrigger(KeyInput.KEY_M));

        inputManager.addListener(actionListener,
                "PitchUp",
                "PitchDown",
                "RollLeft",
                "RollRight",
                "YawLeft",
                "YawRight",
                "ThrottleUp",
                "ThrottleDown",
                "Shoot",
                "InfraredMissile"
        );
    }

    private void configureJoystick() {
        inputManager.addJoystickConnectionListener(
                new JoystickConnectionListener() {
                    @Override
                    public void onConnected(Joystick joystick) {
                        JoystickAxis rollAxis = null;
                        JoystickAxis pitchAxis = null;
                        JoystickAxis yawAxis = null;
                        JoystickAxis leftTriggerAxis = null;
                        JoystickAxis rightTriggerAxis = null;

                        for (JoystickAxis axis : joystick.getAxes()) {
                            if (axis.getLogicalId().equals("x")) {
                                rollAxis = axis;
                            }
                            if (axis.getLogicalId().equals("y")) {
                                pitchAxis = axis;
                            }
                            if (axis.getLogicalId().equals("z")) {
                                yawAxis = axis;
                            }

                            if (axis.getLogicalId().equals("ry")) {
                                leftTriggerAxis = axis;
                            }

                            if (axis.getLogicalId().equals("rx")) {
                                rightTriggerAxis = axis;
                            }
                        }

                        if (rollAxis != null && pitchAxis != null && yawAxis != null && leftTriggerAxis != null && rightTriggerAxis != null) {
                            inputManager.addMapping(
                                    "RollLeftController",
                                    new JoyAxisTrigger(joystick.getJoyId(), rollAxis.getAxisId(), false)
                            );

                            inputManager.addMapping(
                                    "RollRightController",
                                    new JoyAxisTrigger(joystick.getJoyId(), rollAxis.getAxisId(), true)
                            );

                            inputManager.addMapping(
                                    "YawLeftController",
                                    new JoyAxisTrigger(joystick.getJoyId(), yawAxis.getAxisId(), false)
                            );

                            inputManager.addMapping(
                                    "YawRightController",
                                    new JoyAxisTrigger(joystick.getJoyId(), yawAxis.getAxisId(), true)
                            );

                            inputManager.addMapping(
                                    "PitchUpController",
                                    new JoyAxisTrigger(joystick.getJoyId(), pitchAxis.getAxisId(), true)
                            );

                            inputManager.addMapping(
                                    "PitchDownController",
                                    new JoyAxisTrigger(joystick.getJoyId(), pitchAxis.getAxisId(), false)
                            );

                            inputManager.addMapping(
                                    "ThrottleUpController",
                                    new JoyAxisTrigger(joystick.getJoyId(), leftTriggerAxis.getAxisId(), false)
                            );

                            inputManager.addMapping(
                                    "ThrottleDownController",
                                    new JoyAxisTrigger(joystick.getJoyId(), rightTriggerAxis.getAxisId(), false)
                            );
                        }

                        inputManager.addListener(
                                controllerListener,
                                "RollLeftController",
                                "RollRightController",
                                "PitchUpController",
                                "PitchDownController",
                                "YawLeftController",
                                "YawRightController",
                                "ThrottleUpController",
                                "ThrottleDownController"
                        );
                    }

                    @Override
                    public void onDisconnected(Joystick joystick) {
                    }
                }
        );
    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        switch (name) {
            case "PitchUp":
                pitchUp = isPressed;
                break;

            case "PitchDown":
                pitchDown = isPressed;
                break;

            case "RollLeft":
                rollLeft = isPressed;
                break;

            case "RollRight":
                rollRight = isPressed;
                break;

            case "YawLeft":
                yawLeft = isPressed;
                break;

            case "YawRight":
                yawRight = isPressed;
                break;

            case "ThrottleUp":
                throttleUp = isPressed;
                break;

            case "ThrottleDown":
                throttleDown = isPressed;
                break;

            case "Shoot":
                shoot = isPressed;
                break;

            case "InfraredMissile":
                infraredMissile = isPressed;
                break;
        }
    };

    private final AnalogListener controllerListener = (name, value, tpf) -> {
        float stickValue = value / tpf;

        if (stickValue < 0.05f) {
            stickValue = 0f;
        }

        switch (name) {
            case "RollLeftController":
                rollInput = -stickValue;
                break;

            case "RollRightController":
                rollInput = stickValue;
                break;

            case "YawLeftController":
                yawInput = -stickValue;
                break;

            case "YawRightController":
                yawInput = stickValue;
                break;

            case "PitchUpController":
                pitchInput = -stickValue;
                break;

            case "PitchDownController":
                pitchInput = stickValue;
                break;

            case "ThrottleUpController":
                throttleUpInput = stickValue;
                break;

            case "ThrottleDownController":
                throttleDownInput = stickValue;
                break;
        }
    };

    public void resetControllerInput() {
        rollInput = 0f;
        pitchInput = 0f;
        yawInput = 0f;

        throttleUpInput = 0f;
        throttleDownInput = 0f;
    }
}
