package io.github.pixelsam123;

import io.smallrye.mutiny.Uni;

import java.time.Duration;

public class AsyncUtils {

    public static void setTimeout(Runnable callback, Duration delay) {
        Uni
            .createFrom()
            .voidItem()
            .onItem()
            .delayIt()
            .by(delay)
            .subscribe()
            .with(unused -> callback.run());
    }

}
