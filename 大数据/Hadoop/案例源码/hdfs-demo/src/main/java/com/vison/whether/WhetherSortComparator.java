package com.vison.whether;

import com.vison.whether.Whether;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

/**
 * @author Vison
 * @date 2020/7/7 16:20
 * @Version 1.0
 */
public class WhetherSortComparator  extends WritableComparator {


    public WhetherSortComparator() {
        super(Whether.class,true);
    }

    @Override
    public int compare(WritableComparable a, WritableComparable b) {
        Whether whether1 = (Whether)a;
        Whether whether2 = (Whether)b;

        int c1 = whether1.getYear() - whether2.getYear();
        if (c1==0){
            int c2 = whether1.getMonth() - whether2.getMonth();
            if (c2 == 0){
                    //温度采用降序排序，其他日期用升序
                    return -(whether1.getTemp() - whether2.getTemp());
            }
            return c2;
        }
        return c1;
    }
}
