\# 软件工程第11组



\## 项目名称

大学生软件实训教学AI检查评价系统



\## 项目简介

本系统用于大学生软件实训教学成果的 AI 检查与评价，支持多格式成果上传、智能核查、多维度评价与报表导出。



主要功能：

1\. 支持本地大模型服务部署或调用云端大模型服务，具备 PC Web 可视化界面

2\. 实训成果上传与解析：支持 Word/PDF/截图等多格式上传，结合大模型自动解析内容

3\. 智能核查：实训要求与效果校验、逻辑漏洞初步识别、实训步骤完整性核查

4\. 多维度评价管理：自定义评价指标及权重，大模型给出客观评分，预留教师主观评分入口

5\. 报表生成与导出：支持 Excel/PDF 导出，含可视化图表



\## 技术栈



| 层级 | 技术 |

|------|------|

| 前端 | Vue 3 + Vite + Element Plus + ECharts + Vue Router + Pinia |

| 后端 | Java 17 + Spring Boot 3 + Maven |

| 数据库 | MySQL |

| 大模型 | DeepSeek API（主）+ Claude（辅） |

| 文件解析 | Apache POI（Word）、PDFBox（PDF） |

| 报表导出 | EasyExcel（Excel）、iText（PDF） |



\## 目录结构

11-/

├── frontend/ # Vue 前端

├── backend/ # Java Spring Boot 后端

├── docs/ # 项目文档

└── README.md





如何运行:



前端



bash

cd frontend

npm install

npm run dev



浏览器打开：http://localhost:5173/



后端

前提：已安装 JDK 17+、Maven、MySQL



bash

cd backend

mvn spring-boot:run

接口文档：http://127.0.0.1:8000/api/health



开发规范

见 docs/开发规范.md



小组成员：

樊一可、周强强、李则辉、王珂瑞、周洋、阳宇恒



保存关闭后，执行：



```bash

git add README.md

git commit -m "docs: 更新 README，后端改为 Java Spring Boot"

git push

