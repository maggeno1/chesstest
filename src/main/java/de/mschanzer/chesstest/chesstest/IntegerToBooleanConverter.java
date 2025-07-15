package de.mschanzer.chesstest.chesstest; // Oder de.mschanzer.chesstest.chesstest.converter

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter; // Wichtig für Spring Data JDBC

@ReadingConverter
public class IntegerToBooleanConverter implements Converter<Integer, Boolean> {

    @Override
    public Boolean convert(Integer source) {
        // SQLite speichert 0 für false, 1 für true
        return source != null && source == 1;
    }
}