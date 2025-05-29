package com.example.statarbitrage.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.ZonedDateTime;

public class ZonedDateTimeAdapter extends TypeAdapter<ZonedDateTime> {

    @Override
    public void write(JsonWriter out, ZonedDateTime value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString()); // ISO 8601
        }
    }

    @Override
    public ZonedDateTime read(JsonReader in) throws IOException {
        String str = in.nextString();
        return ZonedDateTime.parse(str);
    }
}
