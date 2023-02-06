package io.github.pixelsam123.common.message;

import org.jetbrains.annotations.Nullable;

public interface Message {

    String getType();

    @Nullable Object getContent();

}
