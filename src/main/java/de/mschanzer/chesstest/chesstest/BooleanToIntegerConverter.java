package de.mschanzer.chesstest.chesstest; // Oder de.mschanzer.chesstest.chesstest.converter

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter; // Wichtig für Spring Data JDBC

@WritingConverter
public class BooleanToIntegerConverter implements Converter<Boolean, Integer> {

    @Override
    public Integer convert(Boolean source) {
        // Konvertiert Java Boolean zu Integer für SQLite
        return source != null && source ? 1 : 0;
    }
}