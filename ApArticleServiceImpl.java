import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle>  implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;

    private final static  short MAX_PAGE_SIZE = 50;

    /**
     * Load article list
     * @param dto
         * @param type 1 Load more   2 Load latest
     * @return
     */
    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {
        //1. Validate parameters
        //Validation of pagination count
        Integer size = dto.getSize();
        if(size == null || size == 0){
            size = 10;
        }
        //The pagination value should not exceed 50
        size = Math.min(size,MAX_PAGE_SIZE);


        //Verify parameters  -->type
        if(!type.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !type.equals(ArticleConstants.LOADTYPE_LOAD_NEW)){
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        //Channel parameter validation
        if(StringUtils.isBlank(dto.getTag())){
            dto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        //Time validation
        if(dto.getMaxBehotTime() == null)dto.setMaxBehotTime(new Date());
        if(dto.getMinBehotTime() == null)dto.setMinBehotTime(new Date());

        //2. Query
        List<ApArticle> articleList = apArticleMapper.loadArticleList(dto, type);
        //3. Return results
        return ResponseResult.okResult(articleList);
    }

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ArticleFreemarkerService articleFreemarkerService;

    /**
     * Save related articles on the app
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {

//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        //1. Check parameters
        if(dto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto,apArticle);

        //2. Check if the ID exists
        if(dto.getId() == null){
            //2.1 ID does not exist  Save  Article  Article configuration  Article content

            //Save article
            save(apArticle);

            //Save configuration
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);

            //Save article content
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);

        }else {
            //2.2 ID exists   Modify  Article  Article content

            //Modify article
            updateById(apArticle);

            //Modify article content
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        //Asynchronous call, generate static files and upload to MinIO
        articleFreemarkerService.buildArticleToMinIO(apArticle,dto.getContent());


        //3.Return Article ID
        return ResponseResult.okResult(apArticle.getId());
    }



}
