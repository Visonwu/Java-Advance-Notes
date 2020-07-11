package com.vison.whether;

import com.vison.whether.Whether;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

/**
 * @author Vison
 * @date 2020/7/7 12:59
 * @Version 1.0
 */
public class WhetherGroupComparator extends WritableComparator {


    public WhetherGroupComparator() {
        super(Whether.class,true);
    }

    @Override
    public int compare(WritableComparable a, WritableComparable b) {
        Whether whether1 = (Whether)a;
        Whether whether2 = (Whether)b;

        int c1 = whether1.getYear() - whether2.getYear();
        if (c1==0){
            return whether1.getMonth() - whether2.getMonth();
        }
        return c1;
    }
}
