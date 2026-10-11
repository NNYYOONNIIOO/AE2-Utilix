package com.ae2utilix.block;

public class TileMEChannelListener extends TileMEChannelCaller {
    @Override
    public boolean isListener() {
        return true;
    }

    @Override
    public String getTranslationPrefix() {
        return "ae2_utilix.me_channel_listener";
    }
}
