// IScreenRecorderInterface.aidl
package meugeninua.screenrecording;

import meugeninua.screenrecording.ScreenRecorderParams;
import meugeninua.screenrecording.ScreenRecorderState;

interface IScreenRecorderInterface {

    ScreenRecorderState currentState();
    ScreenRecorderState start(in ScreenRecorderParams params);
    ScreenRecorderState stop();
    ScreenRecorderState flush();
}