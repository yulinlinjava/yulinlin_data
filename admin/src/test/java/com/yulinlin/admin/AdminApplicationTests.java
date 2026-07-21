package com.yulinlin.admin;


import com.yulinlin.admin.dao.UserDao;
import com.yulinlin.admin.dao.UserService;
import com.yulinlin.common.model.ModelDeleteWrapper;
import com.yulinlin.common.model.ModelInsertWrapper;
import com.yulinlin.common.model.ModelSelectWrapper;
import com.yulinlin.data.core.cache.DbCache;
import com.yulinlin.data.core.coder.ICoderManager;
import com.yulinlin.data.core.loadbalan.RandomLoadBalance;
import com.yulinlin.data.core.node.CommandNode;
import com.yulinlin.data.core.parse.ParseType;
import com.yulinlin.data.core.request.QueryRequest;
import com.yulinlin.data.lang.json.JsonUtil;
import com.yulinlin.data.lang.reflection.ReflectAsmUtil;
import com.yulinlin.data.lang.reflection.ReflectionUtil;
import com.yulinlin.data.lang.util.DateTime;
import com.yulinlin.data.lang.util.ListString;
import com.yulinlin.data.lang.util.RandomUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//
@Slf4j
@SpringBootTest
public class AdminApplicationTests {



    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RandomLoadBalance randomLoadBalance;


    @Autowired
    DataSource dataSource;


    @Autowired
    UserDao userDao;

    @Autowired
    DbCache dbCache;

    @Test
    public void test() throws Exception{


        List<SysUserEntity> all = userDao.findAll();

       // userDao.update(all.get(0));
        userDao.updateBatch(all);
    }


    @Autowired
    UserDao dao;

    @Autowired
    UserService userService;

    @Autowired
    ICoderManager coderManager;

    @SneakyThrows
    @Test
    public void selectOne(){


        SysUserEntity sysUserEntity = new SysUserEntity();
        String s = sysUserEntity.nextIncrId();
        String s2 = sysUserEntity.nextIncrId();


        int a = 0;
    }

    @Autowired
    AuthService authService;



    @Test
    public void dispatch() throws Exception {
        List<VideoEntity> list = ModelSelectWrapper.newInstance(VideoEntity.class)
                .page(1,500)
                .selectList();

    /*    for (VideoEntity video : list) {
            video.setHotScore(null);
            video.setTotalShows(null);
            video.setTotalCollects(null);
        }*/
        int rounds = 1000; // 循环次数
        long totalNano = 0; // 总耗时（纳秒）

        System.out.println("==== 克隆性能测试开始 ====");
        System.out.printf("%-8s %-12s %-12s%n", "序号", "耗时 (µs)", "备注");

        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
           // ReflectAsmUtil.clone(list,true);
            ReflectionUtil.clone(list);
            long cost = System.nanoTime() - start;
            totalNano += cost;

            if ((i + 1) % 100 == 0) {
                System.out.printf("%-8d %-12.3f %-12s%n",
                        i + 1, cost / 1000.0, "每100次平均");
            }
        }

        double totalMillis = totalNano / 1_000_000.0;
        double avgMicros = totalNano / (rounds * 1000.0);
        double avgMillis = totalNano / (rounds * 1_000_000.0);
        double throughput = rounds / (totalMillis / 1000.0);

        System.out.println("==== 克隆性能测试汇总 ====");
        System.out.printf("循环次数       : %d%n", rounds);
        System.out.printf("总耗时        : %.3f ms%n", totalMillis);
        System.out.printf("平均耗时      : %.3f µs (≈ %.6f ms)%n", avgMicros, avgMillis);
        System.out.printf("吞吐量        : %.2f 次/秒%n", throughput);
    }



    @Test
    public void xml() throws Exception{

        List<Map> maps = QueryRequest.newInstance(Map.class, CommandNode.of(" select * from post_contents limit 0 ,10 ", ParseType.select))

                .selectList();

        int a = 0;
    }


    private static String  format(String path){
        // data->'$."username"'
        String[] split = path.split("->");

        StringBuffer sb = new StringBuffer();
        for (String s : split) {
            if(sb.length() == 0){
                sb.append(s);
                sb.append("->'$");
            }else {
                sb.append(".\"");
                sb.append(s);
                sb.append("\"");
            }
        }
        sb.append("'");

        return sb.toString();

    }





    public static void main(String[] args) {

        SysUserEntity user = new SysUserEntity();


        //System.out.println(ReflectionUtil.invokeGetter(user, "data.0")); // 输出 dasa

       // SysUserEntity clone = JsonUtil.clone(user);

        Object copy = ReflectionUtil.clone(DateTime.date(), true);
        System.out.println(1); // 输出 dasa
    }

}
