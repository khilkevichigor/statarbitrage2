package com.example.core.converters;

import com.example.shared.models.CointPair;
import com.example.shared.models.TradingPair;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CointPairMapper {
    TradingPair toTradingPair(CointPair cointPair);

    CointPair toCointPair(TradingPair tradingPair);
}
