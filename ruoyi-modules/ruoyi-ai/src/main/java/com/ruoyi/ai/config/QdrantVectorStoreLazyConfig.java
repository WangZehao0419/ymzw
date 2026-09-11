package com.ruoyi.ai.config;

import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant 向量库懒初始化配置
 * <p>
 * 为什么需要：Spring AI 2.0.0-M1 的 QdrantVectorStore 在 afterPropertiesSet 中
 * 会立即通过 gRPC 连接 Qdrant 解析 collection（initialize-schema=true 时还会建库），
 * 若 Qdrant 服务不可达，bean 创建失败将直接阻断整个应用启动。
 * 这里将该 bean 定义改标记为懒初始化，把 Qdrant 连接失败推迟到首次实际使用：
 * - 启动阶段：KnowledgeLoader 首次调用 VectorStore 时触发创建，失败仅 WARN 不阻断启动；
 * - 运行阶段：诊断接口调用时若 Qdrant 仍不可用，按用户决策失败自然抛出。
 * 消费方（KnowledgeLoader / DiagnosisServiceImpl）须配合以 @Lazy 注入，
 * 否则注入点本身就会在启动期触发 bean 创建，懒初始化失效。
 * </p>
 *
 * @author smartartisan
 */
@Configuration
public class QdrantVectorStoreLazyConfig {

    /**
     * BeanFactoryPostProcessor：在单例预实例化之前，
     * 将 QdrantVectorStore 的 bean 定义标记为懒初始化。
     * 声明为 static：避免宿主配置类过早实例化导致 BFPP 注册时序问题。
     */
    @Bean
    public static BeanFactoryPostProcessor qdrantVectorStoreLazyInitProcessor() {
        return beanFactory -> {
            if (beanFactory instanceof DefaultListableBeanFactory dlbf) {
                // allowEagerInit=false：仅按 bean 定义解析类型，不触发任何 bean 实例化
                String[] names = dlbf.getBeanNamesForType(QdrantVectorStore.class, true, false);
                for (String name : names) {
                    BeanDefinition definition = dlbf.getBeanDefinition(name);
                    definition.setLazyInit(true);
                    // 打日志确认生效：懒初始化是启动不阻断的关键开关
                    org.slf4j.LoggerFactory.getLogger(QdrantVectorStoreLazyConfig.class)
                            .info("[Qdrant] QdrantVectorStore bean [{}] 已标记为懒初始化（Qdrant 不可达不阻断启动）", name);
                }
            }
        };
    }
}
