package alxb.flighttraining.game;

import com.jme3.input.InputManager;
import com.jme3.input.Joystick;
import com.jme3.input.JoystickAxis;
import com.jme3.input.JoystickConnectionListener;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.JoyAxisTrigger;
import com.jme3.input.controls.KeyTrigger;

final class FlightInput {
    private final InputManager inputManager;

    // Controller
    float rollInput = 0f;
    float yawInput = 0f;
    float pitchInput = 0f;

    float throttleUpInput = 0f;
    float throttleDownInput = 0f;

    // Tastatur
    boolean pitchUp;
    boolean pitchDown;
    boolean rollLeft;
    boolean rollRight;
    boolean yawLeft;
    boolean yawRight;

    boolean throttleUp;
    boolean throttleDown;

    FlightInput(InputManager inputManager) {
        this.inputManager = inputManager;
        configureJoystick();
        configureKeyboard();
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

        inputManager.addListener(actionListener,
                "PitchUp",
                "PitchDown",
                "RollLeft",
                "RollRight",
                "YawLeft",
                "YawRight",
                "ThrottleUp",
                "ThrottleDown"
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

    void resetControllerInput() {
        rollInput = 0f;
        pitchInput = 0f;
        yawInput = 0f;

        throttleUpInput = 0f;
        throttleDownInput = 0f;
    }
}
