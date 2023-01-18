package io.github.pixelsam123.common.utils;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;

import java.time.Duration;

public class UtilsAsync {

    public static Cancellable setTimeout(Runnable callback, Duration delay) {
        Uni<Void> timeoutUni = Uni.createFrom().voidItem();

        return delay.isZero()
            ? timeoutUni.subscribe().with(unused -> callback.run())
            : timeoutUni.onItem().delayIt().by(delay).subscribe().with(unused -> callback.run());
    }

}
