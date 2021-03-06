package clarity.decoder.prop;

import clarity.decoder.EntityBitStream;
import clarity.decoder.Util;
import clarity.model.Prop;

public class ArrayDecoder implements PropDecoder<Object[]> {

    @Override
    public Object[] decode(EntityBitStream stream, Prop prop) {
        int count = stream.readNumericBits(Util.calcBitsNeededFor(prop.getNumElements() - 1));
        Object[] result = new Object[count];
        PropDecoder<?> decoder = prop.getTemplate().getType().getDecoder();
        int i = 0;
        while (i < count) {
            result[i] = decoder.decode(stream, prop.getTemplate());
            i++;
        }
        return result;
    }

}
