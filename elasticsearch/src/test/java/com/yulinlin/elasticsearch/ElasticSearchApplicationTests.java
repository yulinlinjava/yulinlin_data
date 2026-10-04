package com.yulinlin.elasticsearch;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest

public class ElasticSearchApplicationTests {

    @Autowired
    private ElasticsearchClient client;

    @Test
    public void insert() throws Exception{



    }

    @Test
    public void update(){

    }


    @Test
    public void query() throws Exception{

        Employee employee = new Employee();
        employee.setName("Lily");




    }

    @Test
    public void delete() throws Exception{


    }
    @Test
    public void group() throws Exception{




    }

    @Test
    public void nested(){
        SysDept sysDept = new SysDept();
        sysDept.setId("2");
        sysDept.setTitle("2");
        sysDept.addUser("2","1");
        sysDept.addUser("2","2");
      //  new BaseModelInsertWrapper(sysDept).execute();

    }

    @Test
    public void main(){
     //   TDocument.class;


        Aggregation.Builder builder = new Aggregation.Builder();
        Aggregation build = builder.terms(f -> {
            return f.name("job").field("job");
        })

                .aggregations("avg",f ->f.cardinality( s -> s.field("avg")))
                .build();

        System.out.println(builder.toString());
    }
/*
    @Autowired
    RestHighLevelClient client;

    @Autowired
    ElasticsearchSession searchSession;

    @Test
   public void testCreateIndex() throws IOException {
        //1.创建索引的请求
        CreateIndexRequest request = new CreateIndexRequest("lisen_index");

        //2客户端执行请求，请求后获得响应
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        System.out.println(response);
    }
    @Test
    public void testExistIndex() throws IOException {
        //1.创建索引的请求
        GetIndexRequest request = new GetIndexRequest("lisen_index");
        //2客户端执行请求，请求后获得响应
        boolean exist =  client.indices().exists(request, RequestOptions.DEFAULT);
        System.out.println("测试索引是否存在-----"+exist);
    }

    //删除索引
    @Test
    public void testDeleteIndex() throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest("lisen_index");
        AcknowledgedResponse delete = client.indices().delete(request,RequestOptions.DEFAULT);
        System.out.println("删除索引--------"+delete.isAcknowledged());
    }




    @Test
public    void testAddDocument() throws IOException {



    }



    //测试查询文档
    @Test
    public  void testSearchDocument() throws IOException {


    }


    @Test
    public void update()throws Exception{




        int a = 0;


    }*/

}
