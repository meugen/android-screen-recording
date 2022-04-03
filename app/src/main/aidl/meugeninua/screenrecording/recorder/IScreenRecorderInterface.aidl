// IScreenRecorderInterface.aidl
package meugeninua.screenrecording.recorder;

import meugeninua.screenrecording.recorder.ScreenRecorderParams;
import meugeninua.screenrecording.recorder.ScreenRecorderState;

interface IScreenRecorderInterface {

    ScreenRecorderState currentState();
    ScreenRecorderState start(in ScreenRecorderParams params);
    ScreenRecorderState stop();
    ScreenRecorderState flush();
}