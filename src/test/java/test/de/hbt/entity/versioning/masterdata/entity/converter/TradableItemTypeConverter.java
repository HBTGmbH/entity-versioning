package test.de.hbt.entity.versioning.masterdata.entity.converter;

import javax.persistence.*;

import test.de.hbt.entity.versioning.masterdata.entity.*;

@Converter(autoApply = true)
public class TradableItemTypeConverter implements AttributeConverter<TradableItemType, Character> {

  @Override
  public Character convertToDatabaseColumn(TradableItemType attribute) {
    switch (attribute) {
    case QUALITY:
      return 'Q';
    case ALIAS:
      return 'A';
    case BASKET:
      return 'B';
    default:
      throw new IllegalArgumentException();
    }
  }

  @Override
  public TradableItemType convertToEntityAttribute(Character dbData) {
    switch (dbData) {
    case 'Q':
      return TradableItemType.QUALITY;
    case 'A':
      return TradableItemType.ALIAS;
    case 'B':
      return TradableItemType.BASKET;
    default:
      throw new IllegalArgumentException();
    }
  }
}
