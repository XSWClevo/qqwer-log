// ==UserScript==
// @name         菜大师boss海投助手
// @namespace    https://github.com/zioncai/boss-master
// @version      2.0.0
// @description  菜大师BOSS直聘
// @author       Zion Cai
// @match        https://www.zhipin.com/web/*
// @grant        GM_xmlhttpRequest
// @run-at       document-idle
// @connect      zhipin.com
// @connect      localhost
// @connect      127.0.0.1
// @connect      *.localhost
// @connect      127.0.0.1:5173
// @connect      localhost:5173
// @connect      *
// @connect      api.deepseek.com
// @connect      sub2api.52ai.pro
// @license      MIT
// @noframes
// @downloadURL https://update.greasyfork.org/scripts/581725/%E8%8F%9C%E5%A4%A7%E5%B8%88boss%E6%B5%B7%E6%8A%95%E5%8A%A9%E6%89%8B.user.js
// @updateURL https://update.greasyfork.org/scripts/581725/%E8%8F%9C%E5%A4%A7%E5%B8%88boss%E6%B5%B7%E6%8A%95%E5%8A%A9%E6%89%8B.meta.js
// ==/UserScript==

(function () {
    "use strict";

    /**
     * @typedef {Object} HRInteraction
     * @property {string} hrKey - HR唯一标识
     * @property {boolean} hasGreeted - 是否已打招呼
     * @property {boolean} hasSentResume - 是否已发送简历
     * @property {boolean} hasSentImageResume - 是否已发送图片简历
     */

    /**
     * @typedef {Object} JobInfo
     * @property {string} jobId - 职位ID
     * @property {string} title - 职位标题
     * @property {string} company - 公司名称
     * @property {string} salary - 薪资范围
     * @property {string} location - 工作地点
     * @property {string} hrKey - HR标识
     */

    /**
     * @typedef {Object} GreetingItem
     * @property {string} id - 问候语ID
     * @property {string} content - 问候语内容
     */

    /**
     * @typedef {Object} ImageResume
     * @property {string} id - 图片简历ID
     * @property {string} name - 图片简历名称
     * @property {string} data - Base64编码的图片数据
     */

    /**
     * @typedef {Object} ErrorInfo
     * @property {string} message - 错误消息
     * @property {string} stack - 错误堆栈
     * @property {string} context - 错误上下文
     * @property {string} timestamp - 时间戳
     */

    const CONFIG = {
        BASIC_INTERVAL: 1000,
        OPERATION_INTERVAL: 1200,

        DELAYS: {
            SHORT: 30,
            MEDIUM_SHORT: 200,
        },
        MINI_ICON_SIZE: 40,
        STORAGE_KEYS: {
            PROCESSED_HRS: "processedHRs",
            SENT_GREETINGS_HRS: "sentGreetingsHRs",
            SENT_RESUME_HRS: "sentResumeHRs",
            SENT_IMAGE_RESUME_HRS: "sentImageResumeHRs",
            AI_REPLY_COUNT: "aiReplyCount",
            LAST_AI_DATE: "lastAiDate",
        },
        STORAGE_LIMITS: {
            PROCESSED_HRS: 500,
            SENT_GREETINGS_HRS: 500,
            SENT_RESUME_HRS: 300,
            SENT_IMAGE_RESUME_HRS: 300,
        },

        API: {
            TIMEOUT: 15000,
            GREETING_TIMEOUT: 45000,
            TEST_TIMEOUT: 15000,
            BASE_URL: 'https://leafboss.top/api',
            COMMUNICATION_BASE_URL:
                localStorage.getItem("communicationBaseUrl") || "http://127.0.0.1:8080",
            COMMUNICATION_TOKEN: localStorage.getItem("communicationToken") || "",
            COMMUNICATION_SYNC_ENABLED: JSON.parse(
                localStorage.getItem("communicationSyncEnabled") || "true"
            ),
            RETRY_COUNT: 3,
            RETRY_DELAY: 1000
        },

        UI: {
            MINI_ICON_SIZE: 40,
            ANIMATION_DURATION: 300,
            DEBOUNCE_DELAY: 300
        },

        PERFORMANCE: {
            DOM_CACHE_MAX_AGE: 5000,
            BATCH_SIZE: 10,
            CONCURRENT_LIMIT: 3
        },
        TOKEN_BRIDGE: {
            REQUEST_TYPE: "LOG_ANALYSIS_TOKEN_BRIDGE_REQUEST",
            RESPONSE_TYPE: "LOG_ANALYSIS_TOKEN_BRIDGE_RESPONSE",
            TIMEOUT: 4000,
        },
        DEFAULT_OUTSOURCING_KEYWORDS: [
            "外包",
            "外派",
            "驻场",
            "人力",
            "人力资源",
            "人力服务",
            "人才服务",
            "服务外包",
            "软件外包",
            "技术外包",
            "项目外包",
            "项目派驻",
            "灵活用工",
            "外服",
            "bpo",
            "it外包",
            "软通",
            "软通动力",
            "中软",
            "中软国际",
            "中软国际科技服务",
            "英格玛",
            "佰钧成",
            "博彦",
            "博彦科技",
            "法本",
            "法本信息",
            "微创",
            "微创软件",
            "文思海辉",
            "诚迈",
            "诚迈科技",
            "亚信",
            "亚信科技",
            "东软",
            "东软集团",
            "中电金信",
            "柯莱特",
            "德科",
            "外企德科",
            "fesco",
            "中智",
            "中智湖北",
            "人瑞",
            "人瑞人才",
            "万宝盛华",
            "易才",
            "起点人力",
            "湖北风行楚中",
            "特朗思大宇宙",
            "纬创软件",
            "纬致芯创",
            "宝信软件",
            "武汉宝信信息技术",
            "光谷信息",
            "软帝联合",
            "赛思软件",
            "源启科技",
            "美和易思",
            "汉星联创",
            "儒松科技",
            "上海能良",
            "徽创信息",
            "长亮科技",
            "鸿升教育",
            "如是纵横"
        ]
    };

    const getStoredJSON = (key, defaultValue) => {
        try {
            const val = localStorage.getItem(key);
            return val ? JSON.parse(val) : defaultValue;
        } catch (e) {
            console.error(`Error parsing ${key}:`, e);
            return defaultValue;
        }
    };

    const state = {
        isRunning: false,
        currentIndex: 0,
        processedJobIds: new Set(),

        includeKeywords: [],
        locationKeywords: [],
            excludeCompanyKeywords: [],
            communicationSyncEnabled: getStoredJSON("communicationSyncEnabled", true),
            communicationBaseUrl:
                localStorage.getItem("communicationBaseUrl") || "http://127.0.0.1:8080",
            communicationToken: localStorage.getItem("communicationToken") || "",
            pendingCommunicationJobs: new Map(),
            recentChatReplySyncing: false,
            lastRecentChatReplySyncAt: 0,

        jobList: [],

        session: {
            deliveredCount: 0,
            lastActionWasDelivery: false,
        },

        ui: {
            isMinimized: false,
            theme: localStorage.getItem("theme") || "light",
        },

        hrInteractions: {
            processedHRs: new Set(getStoredJSON("processedHRs", [])),
            sentGreetingsHRs: new Set(getStoredJSON("sentGreetingsHRs", [])),
            sentResumeHRs: new Set(getStoredJSON("sentResumeHRs", [])),
            sentImageResumeHRs: new Set(getStoredJSON("sentImageResumeHRs", [])),
        },

        ai: {
            replyCount: getStoredJSON("aiReplyCount", 0),
            lastAiDate: localStorage.getItem("lastAiDate") || "",
            useAiReply: true,
        },

        settings: {
            useAutoSendResume: getStoredJSON("useAutoSendResume", false),
            aiGreetingEnabled: getStoredJSON("aiGreetingEnabled", true),
            actionDelays: {
                click: parseInt(localStorage.getItem("clickDelay") || "130"),
            },
            jobApplyInterval: parseInt(
                localStorage.getItem("jobApplyInterval") || "5000",
                10
            ),
            jobApplyLimit: parseInt(
                localStorage.getItem("jobApplyLimit") || "0",
                10
            ),
            ai: {
                apiUrl: localStorage.getItem("aiApiUrl") || "",
                apiKey: localStorage.getItem("aiApiKey") || "",
                model: localStorage.getItem("aiModel") || "",
                role:
                    localStorage.getItem("aiRole") ||
                    "回复需满足：20字内，编造专业对口/实习经验/证书任一岗位优势；被问个人信息或岗位条件，直接配合提供合理数据；全程积极真诚无拒绝言辞。",
            },
            autoReply: getStoredJSON("autoReply", false),
            useAutoSendImageResume: getStoredJSON("useAutoSendImageResume", false),
            imageResumeData: localStorage.getItem("imageResumeData") || null,
            communicationSyncEnabled: getStoredJSON("communicationSyncEnabled", true),
            communicationBaseUrl:
                localStorage.getItem("communicationBaseUrl") || "http://127.0.0.1:8080",
            communicationToken:
                localStorage.getItem("communicationToken") || "",
            communicationMode:
                localStorage.getItem("communicationMode") || "new-only",
            recruiterActivityStatus: getStoredJSON(
                "recruiterActivityStatus",
                ["不限"]
            ),
            excludeOutsourcing: getStoredJSON("excludeOutsourcing", false),
            outsourcingKeywords: getStoredJSON(
                "outsourcingKeywords",
                CONFIG.DEFAULT_OUTSOURCING_KEYWORDS
            ),
            excludeHeadhunters: getStoredJSON("excludeHeadhunters", false),
            imageResumes: getStoredJSON("imageResumes", []),
            resume: localStorage.getItem("userResume") || "",
            greetingTemplate: localStorage.getItem("greetingTemplate") || "",
            greetingsList: getStoredJSON("greetingsList", [
                { id: "1", content: "" }
            ]),
        },

        activation: {
            isActivated: localStorage.getItem("activationStatus") === "true",
            activationCode: localStorage.getItem("activationCode") || "",
            cardKey: localStorage.getItem("cardKey") || "",
            activatedAt: localStorage.getItem("activationDate") || "",
        },

        comments: {
            currentCompanyName: "",
            commentsList: [],
            isLoading: false,
            isCommentMode: false,
        },

        securityIdCache: new Map(),
    };

    const elements = {
        panel: null,
        controlBtn: null,
        log: null,
        includeInput: null,
        locationInput: null,
        excludeCompanyInput: null,
        excludeOutsourcingToggle: null,
        outsourcingKeywordsInput: null,
        jobApplyIntervalSelect: null,
        jobApplyLimitInput: null,
        miniIcon: null,
    };

    class ErrorHandler {
        static handle(error, context = '') {
            const errorInfo = {
                message: error.message,
                stack: error.stack,
                context,
                timestamp: new Date().toISOString()
            };

            console.error(`[${context}]`, error);

            if (state.settings && state.settings.errorReporting) {
                this.report(errorInfo);
            }

            return errorInfo;
        }

        static async wrap(fn, context) {
            try {
                return await fn();
            } catch (error) {
                return this.handle(error, context);
            }
        }

        static report(errorInfo) {
            console.log('Error reported:', errorInfo);
        }
    }

    class APIInterceptor {
        static init() {
            const originalOpen = XMLHttpRequest.prototype.open;
            const originalSend = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.open = function (method, url, ...args) {
                this._url = url;
                this._method = method;
                return originalOpen.apply(this, [method, url, ...args]);
            };

            XMLHttpRequest.prototype.send = function (...args) {
                if (this._url && this._url.includes('/wapi/zpgeek/job/detail.json')) {
                    const urlObj = new URL(this._url, window.location.origin);
                    const securityId = urlObj.searchParams.get('securityId');
                    const lid = urlObj.searchParams.get('lid');

                    if (securityId) {
                        const jobIdMatch = this._url.match(/job_detail\/([^.]+)\.html/);
                        const jobId = jobIdMatch ? jobIdMatch[1] : null;

                        state.securityIdCache.set('current', {
                            securityId,
                            lid,
                            jobId,
                            timestamp: Date.now()
                        });

                        console.log(`[API拦截器] 捕获securityId: ${securityId.substring(0, 20)}...`);
                    }
                }

                return originalSend.apply(this, args);
            };

            console.log('[API拦截器] 已启动');
        }

        static getCurrentSecurityId() {
            const cached = state.securityIdCache.get('current');
            if (cached && Date.now() - cached.timestamp < 300000) {
                return cached;
            }
            return null;
        }
    }

    class DOMCache {
        static cache = new Map();
        static maxAge = CONFIG.PERFORMANCE.DOM_CACHE_MAX_AGE;

        static get(selector) {
            const cached = this.cache.get(selector);
            if (cached && Date.now() - cached.time < this.maxAge) {
                return cached.element;
            }

            const element = document.querySelector(selector);
            if (element) {
                this.cache.set(selector, { element, time: Date.now() });
            }
            return element;
        }

        static getAll(selector) {
            return document.querySelectorAll(selector);
        }

        static clear() {
            this.cache.clear();
        }

        static remove(selector) {
            this.cache.delete(selector);
        }
    }

    class ManagedSet {
        constructor(maxSize = 500) {
            this.items = new Set();
            this.maxSize = maxSize;
        }

        add(item) {
            if (this.items.size >= this.maxSize) {
                const firstItem = this.items.values().next().value;
                this.items.delete(firstItem);
            }
            this.items.add(item);
        }

        has(item) {
            return this.items.has(item);
        }

        delete(item) {
            return this.items.delete(item);
        }

        clear() {
            this.items.clear();
        }

        get size() {
            return this.items.size;
        }

        toArray() {
            return Array.from(this.items);
        }
    }

    class EventManager {
        static listeners = new Map();

        static add(element, event, handler, options = {}) {
            const key = `${element.id || element.className || element.tagName}-${event}-${Date.now()}`;

            if (this.listeners.has(key)) {
                this.remove(key);
            }

            element.addEventListener(event, handler, options);
            this.listeners.set(key, { element, event, handler });
            return key;
        }

        static remove(key) {
            const listener = this.listeners.get(key);
            if (listener) {
                listener.element.removeEventListener(
                    listener.event,
                    listener.handler
                );
                this.listeners.delete(key);
            }
        }

        static removeAll() {
            this.listeners.forEach((_, key) => this.remove(key));
        }

        static getByElement(element) {
            const results = [];
            this.listeners.forEach((listener, key) => {
                if (listener.element === element) {
                    results.push({ key, ...listener });
                }
            });
            return results;
        }
    }

    class DOMUtils {
        static async waitForAndAct(selector, action, options = {}) {
            const {
                timeout = 5000,
                retryInterval = 100,
                maxRetries = 3
            } = options;

            for (let i = 0; i < maxRetries; i++) {
                try {
                    const element = await Core.waitForElement(selector, timeout);
                    if (element) {
                        const result = await action(element);
                        return result;
                    }
                } catch (error) {
                    if (i === maxRetries - 1) throw error;
                    await Core.delay(retryInterval);
                }
            }
            return null;
        }

        static async clickElement(selector, options = {}) {
            return this.waitForAndAct(selector, async (element) => {
                await Core.simulateClick(element);
                return true;
            }, options);
        }

        static async inputText(selector, text, options = {}) {
            return this.waitForAndAct(selector, async (element) => {
                element.textContent = "";
                element.focus();
                document.execCommand("insertText", false, text);
                return true;
            }, options);
        }

        static debounce(fn, delay = CONFIG.UI.DEBOUNCE_DELAY) {
            let timer = null;
            return function (...args) {
                if (timer) clearTimeout(timer);
                timer = setTimeout(() => fn.apply(this, args), delay);
            };
        }

        static throttle(fn, delay = CONFIG.UI.DEBOUNCE_DELAY) {
            let lastTime = 0;
            return function (...args) {
                const now = Date.now();
                if (now - lastTime >= delay) {
                    lastTime = now;
                    return fn.apply(this, args);
                }
            };
        }
    }

    class StorageManager {
        static setItem(key, value) {
            try {
                localStorage.setItem(
                    key,
                    typeof value === "string" ? value : JSON.stringify(value)
                );
                return true;
            } catch (error) {
                Core.log(`设置存储项 ${key} 失败: ${error.message}`);
                return false;
            }
        }

        static getItem(key, defaultValue = null) {
            try {
                const value = localStorage.getItem(key);
                return value !== null ? value : defaultValue;
            } catch (error) {
                Core.log(`获取存储项 ${key} 失败: ${error.message}`);
                return defaultValue;
            }
        }

        static addRecordWithLimit(storageKey, record, currentSet, limit) {
            try {
                if (currentSet.has(record)) {
                    return;
                }

                let records = this.getParsedItem(storageKey, []);
                records = Array.isArray(records) ? records : [];

                if (records.length >= limit) {
                    records.shift();
                }

                records.push(record);
                currentSet.add(record);
                this.setItem(storageKey, records);

                console.log(
                    `存储管理: 添加记录${records.length >= limit ? "并删除最早记录" : ""
                    }，当前${storageKey}数量: ${records.length}/${limit}`
                );
            } catch (error) {
                console.log(`存储管理出错: ${error.message}`);
            }
        }

        static getParsedItem(storageKey, defaultValue = []) {
            try {
                const data = this.getItem(storageKey);
                return data ? JSON.parse(data) : defaultValue;
            } catch (error) {
                Core.log(`解析存储记录出错: ${error.message}`);
                return defaultValue;
            }
        }

        static ensureStorageLimits() {
            const limitConfigs = [
                {
                    key: CONFIG.STORAGE_KEYS.PROCESSED_HRS,
                    set: state.hrInteractions.processedHRs,
                    limit: CONFIG.STORAGE_LIMITS.PROCESSED_HRS,
                },
                {
                    key: CONFIG.STORAGE_KEYS.SENT_GREETINGS_HRS,
                    set: state.hrInteractions.sentGreetingsHRs,
                    limit: CONFIG.STORAGE_LIMITS.SENT_GREETINGS_HRS,
                },
                {
                    key: CONFIG.STORAGE_KEYS.SENT_RESUME_HRS,
                    set: state.hrInteractions.sentResumeHRs,
                    limit: CONFIG.STORAGE_LIMITS.SENT_RESUME_HRS,
                },
                {
                    key: CONFIG.STORAGE_KEYS.SENT_IMAGE_RESUME_HRS,
                    set: state.hrInteractions.sentImageResumeHRs,
                    limit: CONFIG.STORAGE_LIMITS.SENT_IMAGE_RESUME_HRS,
                },
            ];

            limitConfigs.forEach(({ key, set, limit }) => {
                const records = this.getParsedItem(key, []);
                if (records.length > limit) {
                    const trimmedRecords = records.slice(-limit);
                    this.setItem(key, trimmedRecords);

                    set.clear();
                    trimmedRecords.forEach((record) => set.add(record));

                    console.log(
                        `存储管理: 清理${key}记录，从${records.length}减少到${trimmedRecords.length}`
                    );
                }
            });
        }
    }

    class StatePersistence {
        static saveState() {
            try {
                const stateMap = {
                    aiReplyCount: state.ai.replyCount,
                    lastAiDate: state.ai.lastAiDate,

                    useAiReply: state.ai.useAiReply,
                    useAutoSendResume: state.settings.useAutoSendResume,
                    useAutoSendImageResume: state.settings.useAutoSendImageResume,
                    imageResumeData: state.settings.imageResumeData,
                    imageResumes: state.settings.imageResumes || [],
                    greetingsList: state.settings.greetingsList || [],
                    theme: state.ui.theme,
                    clickDelay: state.settings.actionDelays.click,
                    includeKeywords: state.includeKeywords,
                    locationKeywords: state.locationKeywords,
                    excludeCompanyKeywords: state.excludeCompanyKeywords,
                    outsourcingKeywords:
                        state.settings.outsourcingKeywords ||
                        CONFIG.DEFAULT_OUTSOURCING_KEYWORDS,
                };

                Object.entries(stateMap).forEach(([key, value]) => {
                    StorageManager.setItem(key, value);
                });
            } catch (error) {
                Core.log(`保存状态失败: ${error.message}`);
            }
        }

        static loadState() {
            try {
                state.includeKeywords = StorageManager.getParsedItem(
                    "includeKeywords",
                    []
                );
                state.locationKeywords =
                    StorageManager.getParsedItem("locationKeywords") ||
                    StorageManager.getParsedItem("excludeKeywords", []);
                state.excludeCompanyKeywords = StorageManager.getParsedItem(
                    "excludeCompanyKeywords",
                    []
                );
                state.settings.outsourcingKeywords = StorageManager.getParsedItem(
                    "outsourcingKeywords",
                    CONFIG.DEFAULT_OUTSOURCING_KEYWORDS
                );

                const imageResumes = StorageManager.getParsedItem("imageResumes", []);
                if (Array.isArray(imageResumes))
                    state.settings.imageResumes = imageResumes;

                const greetingsList = StorageManager.getParsedItem("greetingsList", []);
                if (Array.isArray(greetingsList))
                    state.settings.greetingsList = greetingsList;

                StorageManager.ensureStorageLimits();
            } catch (error) {
                Core.log(`加载状态失败: ${error.message}`);
            }
        }
    }

    class HRInteractionManager {
        static getConversationKey(hrKey) {
            return Core.buildHrConversationKey?.() || hrKey;
        }

        static async sendResumeOnceForConversation(hrKey) {
            const conversationKey = this.getConversationKey(hrKey);
            if (
                state.hrInteractions.sentResumeHRs.has(conversationKey) ||
                state.hrInteractions.sentResumeHRs.has(hrKey)
            ) {
                Core.log(`本会话已发送过简历，跳过重复发送: ${conversationKey}`);
                return false;
            }
            const sentResume = await this.sendResume();
            if (sentResume) {
                StorageManager.addRecordWithLimit(
                    CONFIG.STORAGE_KEYS.SENT_RESUME_HRS,
                    conversationKey,
                    state.hrInteractions.sentResumeHRs,
                    CONFIG.STORAGE_LIMITS.SENT_RESUME_HRS
                );
                if (conversationKey !== hrKey) {
                    state.hrInteractions.sentResumeHRs.add(hrKey);
                }
                Core.log(`已向 ${conversationKey} 发送简历`);
            }
            return sentResume;
        }

        static async sendImageResumeOnceForConversation(hrKey) {
            const conversationKey = this.getConversationKey(hrKey);
            if (
                state.hrInteractions.sentImageResumeHRs.has(conversationKey) ||
                state.hrInteractions.sentImageResumeHRs.has(hrKey)
            ) {
                Core.log(`本会话已发送过图片简历，跳过重复发送: ${conversationKey}`);
                return false;
            }
            const sentImageResume = await this.sendImageResume();
            if (sentImageResume) {
                StorageManager.addRecordWithLimit(
                    CONFIG.STORAGE_KEYS.SENT_IMAGE_RESUME_HRS,
                    conversationKey,
                    state.hrInteractions.sentImageResumeHRs,
                    CONFIG.STORAGE_LIMITS.SENT_IMAGE_RESUME_HRS
                );
                if (conversationKey !== hrKey) {
                    state.hrInteractions.sentImageResumeHRs.add(hrKey);
                }
                Core.log(`已向 ${conversationKey} 发送图片简历`);
            }
            return sentImageResume;
        }

        /**
         * 处理HR交互
         * @param {string} hrKey - HR唯一标识
         * @returns {Promise<void>}
         */
        static async handleHRInteraction(hrKey) {
            const hasResponded = await this.hasHRResponded();
            const conversationKey = this.getConversationKey(hrKey);

            if (!state.hrInteractions.sentGreetingsHRs.has(hrKey)) {
                await this._handleFirstInteraction(hrKey);
                return;
            }

            if (
                !state.hrInteractions.sentResumeHRs.has(conversationKey) ||
                !state.hrInteractions.sentImageResumeHRs.has(conversationKey)
            ) {
                if (hasResponded) {
                    await this._handleFollowUpResponse(hrKey);
                }
                return;
            }

            await Core.aiReply();
        }

        static async _handleFirstInteraction(hrKey) {
            Core.log(`首次沟通: ${hrKey}`);

            const sentGreeting = await this.sendGreetings();
            if (sentGreeting) {
                StorageManager.addRecordWithLimit(
                    CONFIG.STORAGE_KEYS.SENT_GREETINGS_HRS,
                    hrKey,
                    state.hrInteractions.sentGreetingsHRs,
                    CONFIG.STORAGE_LIMITS.SENT_GREETINGS_HRS
                );
            }

            await this._handleResumeSending(hrKey);
        }

        static async _handleResumeSending(hrKey) {
            if (
                state.settings.useAutoSendResume &&
                !state.hrInteractions.sentResumeHRs.has(this.getConversationKey(hrKey))
            ) {
                await this.sendResumeOnceForConversation(hrKey);
            }

            if (
                state.settings.useAutoSendImageResume &&
                !state.hrInteractions.sentImageResumeHRs.has(this.getConversationKey(hrKey))
            ) {
                await this.sendImageResumeOnceForConversation(hrKey);
            }
        }

        static async _handleFollowUpResponse(hrKey) {
            if (this.hasCardMessage()) {
                const handled = await this.handleCardMessage(hrKey);
                if (handled) {
                    return;
                }
            }

            const lastMessage = await Core.getLastFriendMessageText();
            const cleanedLastMessage = Core.cleanMessage(lastMessage || "");

            if (cleanedLastMessage && Core.isNotSuitableReply(cleanedLastMessage)) {
                Core.log(`HR回复包含拒绝话术，跳过自动发送简历: ${hrKey} | ${cleanedLastMessage}`);
                return;
            }

            if (
                cleanedLastMessage &&
                (cleanedLastMessage.includes("简历") || cleanedLastMessage.includes("发送简历"))
            ) {
                Core.log(`HR提到"简历"，发送简历: ${hrKey}`);

                if (
                    state.settings.useAutoSendImageResume &&
                    !state.hrInteractions.sentImageResumeHRs.has(this.getConversationKey(hrKey))
                ) {
                    const sentImageResume = await this.sendImageResumeOnceForConversation(hrKey);
                    if (sentImageResume) {
                        return;
                    }
                }

                if (!state.hrInteractions.sentResumeHRs.has(this.getConversationKey(hrKey))) {
                    await this.sendResumeOnceForConversation(hrKey);
                }
            }
        }

        /**
         * 发送自定义回复
         * @param {string} replyText - 回复文本
         * @returns {Promise<boolean>} 是否发送成功
         */
        static async sendCustomReply(replyText) {
            try {
                const inputBox = await Core.waitForElement("#chat-input");
                if (!inputBox) {
                    Core.log("未找到聊天输入框");
                    return false;
                }

                inputBox.textContent = "";
                inputBox.focus();
                document.execCommand("insertText", false, replyText);
                await Core.delay(CONFIG.OPERATION_INTERVAL / 10);

                const sendButton = DOMCache.get(".btn-send");
                if (sendButton) {
                    await Core.simulateClick(sendButton);
                } else {
                    const enterKeyEvent = new KeyboardEvent("keydown", {
                        key: "Enter",
                        keyCode: 13,
                        code: "Enter",
                        which: 13,
                        bubbles: true,
                    });
                    inputBox.dispatchEvent(enterKeyEvent);
                }

                return true;
            } catch (error) {
                ErrorHandler.handle(error, 'HRInteractionManager.sendCustomReply');
                Core.log(`发送自定义回复出错: ${error.message}`);
                return false;
            }
        }

        static async hasHRResponded() {
            await Core.delay(state.settings.actionDelays.click);

            const chatContainer = DOMCache.get(".chat-message .im-list");
            if (!chatContainer) return false;

            const friendMessages = Array.from(
                chatContainer.querySelectorAll("li.message-item.item-friend")
            );
            return friendMessages.length > 0;
        }

        static hasCardMessage() {
            try {
                const chatContainer = DOMCache.get(".chat-message .im-list");
                if (!chatContainer) return false;

                const friendMessages = Array.from(
                    chatContainer.querySelectorAll("li.message-item.item-friend")
                );
                if (friendMessages.length === 0) return false;

                const lastMessageEl = friendMessages[friendMessages.length - 1];
                const cardWrap = lastMessageEl.querySelector(".message-card-wrap");
                return cardWrap !== null;
            } catch (error) {
                Core.log(`检测卡片消息出错: ${error.message}`);
                return false;
            }
        }

        static async handleCardMessage(hrKey) {
            try {
                const chatContainer = DOMCache.get(".chat-message .im-list");
                if (!chatContainer) {
                    Core.log("未找到聊天容器");
                    return false;
                }

                const friendMessages = Array.from(
                    chatContainer.querySelectorAll("li.message-item.item-friend")
                );
                if (friendMessages.length === 0) {
                    Core.log("未找到HR消息");
                    return false;
                }

                const lastMessageEl = friendMessages[friendMessages.length - 1];
                const cardButtons = lastMessageEl.querySelectorAll(".card-btn");

                if (!cardButtons || cardButtons.length === 0) {
                    Core.log("未找到卡片按钮");
                    return false;
                }

                for (const btn of cardButtons) {
                    if (btn.textContent.trim() === "同意") {
                        await Core.simulateClick(btn);
                        await Core.delay(state.settings.actionDelays.click);
                        return true;
                    }
                }

                Core.log(`未找到"同意"按钮`);
                return false;
            } catch (error) {
                Core.log(`处理卡片消息出错: ${error.message}`);
                return false;
            }
        }

        static async sendGreetings() {
            try {
                if (
                    !state.settings.greetingsList ||
                    state.settings.greetingsList.length === 0
                ) {
                    return false;
                }

                for (let i = 0; i < state.settings.greetingsList.length; i++) {
                    const greeting = state.settings.greetingsList[i];
                    if (!greeting.content || !greeting.content.trim()) {
                        continue;
                    }
                    Core.log(
                        `发送自我介绍：第${i + 1}条/共${state.settings.greetingsList.length}条`
                    );
                    await this.sendCustomReply(greeting.content);
                    await Core.delay(state.settings.actionDelays.click);
                }

                return true;
            } catch (error) {
                Core.log(`发送自我介绍出错: ${error.message}`);
                return false;
            }
        }

        static _findMatchingResume(resumeItems, positionName) {
            try {
                const positionNameLower = positionName.toLowerCase();
                const twoCharKeywords = Core.extractTwoCharKeywords(positionNameLower);

                for (const keyword of twoCharKeywords) {
                    for (const item of resumeItems) {
                        const resumeNameElement = item.querySelector(".resume-name");
                        if (!resumeNameElement) continue;

                        const resumeName = resumeNameElement.textContent
                            .trim()
                            .toLowerCase();

                        if (resumeName.includes(keyword)) {
                            const resumeNameText = resumeNameElement.textContent.trim();
                            Core.log(`智能匹配: "${resumeNameText}" 依据: "${keyword}"`);
                            return item;
                        }
                    }
                }

                return null;
            } catch (error) {
                Core.log(`简历匹配出错: ${error.message}`);
                return null;
            }
        }

        static async sendResume() {
            try {
                const resumeBtn = await Core.waitForElement(() => {
                    return [...document.querySelectorAll(".toolbar-btn")].find(
                        (el) => el.textContent.trim() === "发简历"
                    );
                });

                if (!resumeBtn) {
                    Core.log("无法发送简历，未找到发简历按钮");
                    return false;
                }

                if (resumeBtn.classList.contains("unable")) {
                    Core.log("对方未回复，您无权发送简历");
                    return false;
                }

                let positionName = Core.getPositionName();
                if (!positionName) {
                    Core.log("未找到岗位名称元素");
                }

                await Core.simulateClick(resumeBtn);
                await Core.smartDelay(state.settings.actionDelays.click, "click");
                await Core.smartDelay(800, "resume_load");

                const confirmDialog = document.querySelector(
                    ".panel-resume.sentence-popover"
                );
                if (confirmDialog) {
                    Core.log("您只有一份附件简历");

                    const confirmBtn = confirmDialog.querySelector(".btn-sure-v2");
                    if (!confirmBtn) {
                        Core.log("未找到确认按钮");
                        return false;
                    }

                    await Core.simulateClick(confirmBtn);
                    return true;
                }

                const resumeList = await Core.waitForElement("ul.resume-list");
                if (!resumeList) {
                    Core.log("未找到简历列表");
                    return false;
                }

                const resumeItems = Array.from(
                    resumeList.querySelectorAll("li.list-item")
                );
                if (resumeItems.length === 0) {
                    Core.log("未找到简历列表项");
                    return false;
                }

                let selectedResumeItem = null;
                if (positionName) {
                    selectedResumeItem = this._findMatchingResume(
                        resumeItems,
                        positionName
                    );
                }

                if (!selectedResumeItem) {
                    selectedResumeItem = resumeItems[0];
                    const resumeName = selectedResumeItem
                        .querySelector(".resume-name")
                        .textContent.trim();
                    Core.log('使用第一个简历: "' + resumeName + '"');
                }

                await Core.simulateClick(selectedResumeItem);
                await Core.smartDelay(state.settings.actionDelays.click, "click");
                await Core.smartDelay(500, "selection");

                const sendBtn = await Core.waitForElement(
                    "button.btn-v2.btn-sure-v2.btn-confirm"
                );
                if (!sendBtn) {
                    Core.log("未找到发送按钮");
                    return false;
                }

                if (sendBtn.disabled) {
                    Core.log("发送按钮不可用，可能简历未正确选择");
                    return false;
                }

                await Core.simulateClick(sendBtn);
                return true;
            } catch (error) {
                Core.log(`发送简历出错: ${error.message}`);
                return false;
            }
        }

        static selectImageResume(positionName) {
            try {
                const positionNameLower = positionName.toLowerCase();

                if (state.settings.imageResumes.length === 1) {
                    return state.settings.imageResumes[0];
                }

                const twoCharKeywords = Core.extractTwoCharKeywords(positionNameLower);

                for (const keyword of twoCharKeywords) {
                    for (const resume of state.settings.imageResumes) {
                        const resumeNameLower = resume.path.toLowerCase();

                        if (resumeNameLower.includes(keyword)) {
                            Core.log(`智能匹配: "${resume.path}" 依据: "${keyword}"`);
                            return resume;
                        }
                    }
                }

                return state.settings.imageResumes[0];
            } catch (error) {
                Core.log(`选择图片简历出错: ${error.message}`);
                return state.settings.imageResumes[0] || null;
            }
        }

        static async sendImageResume() {
            try {
                if (
                    !state.settings.useAutoSendImageResume ||
                    !state.settings.imageResumes ||
                    state.settings.imageResumes.length === 0
                ) {
                    return false;
                }

                let positionName = Core.getPositionName();
                if (!positionName) {
                    Core.log("未找到岗位名称元素");
                }

                const selectedResume = this.selectImageResume(positionName);

                if (!selectedResume || !selectedResume.data) {
                    Core.log("没有可发送的图片简历数据");
                    return false;
                }

                const imageSendBtn = await Core.waitForElement(
                    '.toolbar-btn-content.icon.btn-sendimg input[type="file"]'
                );
                if (!imageSendBtn) {
                    Core.log("未找到图片发送按钮");
                    return false;
                }

                const byteCharacters = atob(selectedResume.data.split(",")[1]);
                const byteNumbers = new Array(byteCharacters.length);
                for (let i = 0; i < byteCharacters.length; i++) {
                    byteNumbers[i] = byteCharacters.charCodeAt(i);
                }
                const byteArray = new Uint8Array(byteNumbers);
                const blob = new Blob([byteArray], { type: "image/jpeg" });

                const file = new File([blob], selectedResume.path, {
                    type: "image/jpeg",
                    lastModified: new Date().getTime(),
                });

                const dataTransfer = new DataTransfer();
                dataTransfer.items.add(file);

                imageSendBtn.files = dataTransfer.files;

                const event = new Event("change", { bubbles: true });
                imageSendBtn.dispatchEvent(event);
                return true;
            } catch (error) {
                Core.log(`发送图片出错: ${error.message}`);
                return false;
            }
        }
    }

    const UI = {
        PAGE_TYPES: {
            JOB_LIST: "jobList",
            CHAT: "chat",
        },

        currentPageType: null,

        init() {
            this.currentPageType = location.pathname.includes("/chat")
                ? this.PAGE_TYPES.CHAT
                : this.PAGE_TYPES.JOB_LIST;
            this._applyTheme();
            this.createControlPanel();
            this.createMiniIcon();

            if (this.currentPageType === this.PAGE_TYPES.JOB_LIST && !state.isRunning) {
                setTimeout(() => {
                    Core.loadAndDisplayComments();
                }, 500);
            }


            document.addEventListener("click",function(e){var card=e.target.closest("li.job-card-box");if(card&&settings.aiGreetingEnabled&&settings.ai.apiKey&&settings.resume&&!state.isRunning){setTimeout(async function(){var ji=Core.extractJobDetail();if(ji&&ji.title){await Core.generateGreeting(ji);var ps=document.getElementById("greeting-preview-section");if(ps)ps.style.display="block";}},1500);}});
        },

        createControlPanel() {
            if (document.getElementById("boss-pro-panel")) {
                document.getElementById("boss-pro-panel").remove();
            }

            elements.panel = this._createPanel();

            const header = this._createHeader();
            const controls = this._createPageControls();
            elements.log = this._createLogger();
            const footer = this._createFooter();

            elements.panel.append(header, controls, elements.log, footer);
            document.body.appendChild(elements.panel);
            this._makeDraggable(elements.panel);
        },

        _applyTheme() {
            CONFIG.COLORS =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? this.THEMES.JOB_LIST
                    : this.THEMES.CHAT;

            document.documentElement.style.setProperty(
                "--primary-color",
                CONFIG.COLORS.primary
            );
            document.documentElement.style.setProperty(
                "--secondary-color",
                CONFIG.COLORS.secondary
            );
            document.documentElement.style.setProperty(
                "--accent-color",
                CONFIG.COLORS.accent
            );
            document.documentElement.style.setProperty(
                "--neutral-color",
                CONFIG.COLORS.neutral
            );
        },

        THEMES: {
            JOB_LIST: {
                primary: "#4F46E5",
                secondary: "#f5f7fa",
                accent: "#e8f0fe",
                neutral: "#6b7280",
            },
            CHAT: {
                primary: "#34a853",
                secondary: "#f0fdf4",
                accent: "#dcfce7",
                neutral: "#6b7280",
            },
        },

        _createPanel() {
            const panel = document.createElement("div");
            panel.id = "boss-pro-panel";
            panel.className =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? "boss-joblist-panel"
                    : "boss-chat-panel";

            const baseStyles = `
            position: fixed;
            top: 36px;
            right: 24px;
            width: clamp(300px, 80vw, 400px);
            border-radius: 12px;
            padding: 12px;
            font-family: 'Segoe UI', system-ui, sans-serif;
            z-index: 2147483647;
            display: flex;
            flex-direction: column;
            transition: all 0.3s ease;
            background: #ffffff;
            box-shadow: 0 10px 25px rgba(var(--primary-rgb), 0.15);
            border: 1px solid var(--accent-color);
            cursor: default;
        `;

            panel.style.cssText = baseStyles;

            const rgbColor = this._hexToRgb(CONFIG.COLORS.primary);
            document.documentElement.style.setProperty("--primary-rgb", rgbColor);

            return panel;
        },

        _createHeader() {
            const header = document.createElement("div");
            header.className =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? "boss-header"
                    : "boss-chat-header";

            header.style.cssText = `
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0 10px 15px;
            margin-bottom: 15px;
            border-bottom: 1px solid var(--accent-color);
        `;

            const title = this._createTitle();

            const buttonContainer = document.createElement("div");
            buttonContainer.style.cssText = `
            display: flex;
            gap: 8px;
        `;

            const buttonTitles =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? {
                        activate: "激活插件",
                        settings: "插件设置",
                        close: "最小化海投面板",
                    }
                    : {
                        activate: "激活插件",
                        settings: "海投设置",
                        close: "最小化聊天面板",
                    };

            var settingsBtn=this._createIconButton("⚙",function(){try{showSettingsDialog();}catch(e){alert("BTN: "+e.message);}},buttonTitles.settings);
            settingsBtn.onclick=function(){alert("onclick!");try{showSettingsDialog();}catch(e){alert("S: "+e.message);}};
            var closeBtn=this._createIconButton("✕",function(){state.isMinimized=true;elements.panel.style.transform="translateY(160%)";elements.miniIcon.style.display="flex";},buttonTitles.close);
            closeBtn.onclick=function(){alert("close clicked");state.isMinimized=true;elements.panel.style.transform="translateY(160%)";elements.miniIcon.style.display="flex";};
            buttonContainer.append(settingsBtn,closeBtn);
            header.append(title, buttonContainer);
            return header;
        },

        _createTitle() {
            const title = document.createElement("div");
            title.style.display = "flex";
            title.style.alignItems = "center";
            title.style.gap = "10px";

            const customSvg = `
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 80" style="width:100%;height:100%;"><rect width="80" height="80" rx="14" fill="#4F46E5"/><g fill="white"><rect x="25" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="15" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="15" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="60" width="4.7" height="4.7" rx="0.5"/></g></svg>
    `;

            const titleConfig =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? {
                        main: `<span style="color:var(--primary-color);">BOSS</span>海投助手`,
                        sub: "AI智能 · 半自动投递",
                    }
                    : {
                        main: `<span style="color:var(--primary-color);">BOSS</span>智能聊天`,
                        sub: "智能对话 · 高效沟通",
                    };

            title.innerHTML = `
        <div style="
            width: 40px;
            height: 40px;
            background: var(--primary-color);
            border-radius: 10px;
            display: flex;
            justify-content: center;
            align-items: center;
            color: white;
            font-weight: bold;
            box-shadow: 0 2px 8px rgba(var(--primary-rgb), 0.3);
        ">
            ${customSvg}
        </div>
        <div>
            <h3 style="
                margin: 0;
                color: #2c3e50;
                font-weight: 600;
                font-size: 1.2rem;
            ">
                ${titleConfig.main}
            </h3>
            <span style="
                font-size:0.8em;
                color:var(--neutral-color);
            ">
                ${titleConfig.sub}
            </span>
        </div>
    `;

            return title;
        },

        _createPageControls() {
            if (this.currentPageType === this.PAGE_TYPES.JOB_LIST) {
                return this._createJobListControls();
            } else {
                return this._createChatControls();
            }
        },

        _createJobListControls() {
            const container = document.createElement("div");
            container.className = "boss-joblist-controls";
            container.style.marginBottom = "15px";
            container.style.padding = "0 10px";

            const filterContainer = this._createFilterContainer();

            container.append(filterContainer);
            return container;
        },

        _createChatControls() {
            const container = document.createElement("div");
            container.className = "boss-chat-controls";
            container.style.cssText = `
            background: var(--secondary-color);
            border-radius: 12px;
            padding: 15px;
            margin-left: 10px;
            margin-right: 10px;
            margin-bottom: 15px;
        `;

            const configRow = document.createElement("div");
            configRow.style.cssText = `
            display: flex;
            gap: 10px;
            margin-bottom: 15px;
        `;

            const communicationIncludeCol = this._createInputControl(
                "沟通岗位包含：",
                "communication-include",
                "如：技术,产品,设计"
            );

            const communicationModeCol = this._createSelectControl(
                "沟通模式：",
                "communication-mode-selector",
                [
                    { value: "new-only", text: "仅新消息" },
                    { value: "auto", text: "自动轮询" },
                ]
            );

            elements.communicationIncludeInput =
                communicationIncludeCol.querySelector("input");
            elements.communicationModeSelector =
                communicationModeCol.querySelector("select");
            configRow.append(communicationIncludeCol, communicationModeCol);

            elements.communicationModeSelector.addEventListener("change", (e) => {
                settings.communicationMode = e.target.value;
                saveSettings();
            });

            elements.communicationIncludeInput.addEventListener("input", (e) => {
                settings.communicationIncludeKeywords = e.target.value;
                saveSettings();
            });

            elements.controlBtn = this._createTextButton(
                "开始智能聊天",
                "var(--primary-color)",
                () => {
                    toggleChatProcess();
                }
            );

            container.append(configRow, elements.controlBtn);
            return container;
        },

        _createFilterContainer() {
            const filterContainer = document.createElement("div");
            filterContainer.style.cssText = `
            background: var(--secondary-color);
            border-radius: 12px;
            padding: 15px;
            margin-bottom: 0px;
        `;

            const filterRow = document.createElement("div");
            filterRow.style.cssText = `
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        `;

            const includeFilterCol = this._createInputControl(
                "职位名包含：",
                "include-filter",
                "如：前端，开发"
            );
            const locationFilterCol = this._createInputControl(
                "工作地包含：",
                "location-filter",
                "如：杭州，滨江"
            );
            const excludeCompanyCol = this._createInputControl(
                "排除公司：",
                "exclude-company-filter",
                "如：外包，人力，某某科技"
            );
            const outsourcingKeywordsCol = this._createInputControl(
                "外包关键词：",
                "outsourcing-keywords-filter",
                "如：外包，外派，驻场，人力，BPO"
            );

            elements.includeInput = includeFilterCol.querySelector("input");
            elements.locationInput = locationFilterCol.querySelector("input");
            elements.excludeCompanyInput = excludeCompanyCol.querySelector("input");
            elements.outsourcingKeywordsInput =
                outsourcingKeywordsCol.querySelector("input");

            filterRow.append(includeFilterCol, locationFilterCol);
            filterContainer.appendChild(filterRow);

            const excludeRow = document.createElement("div");
            excludeRow.style.cssText = `
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        `;
            excludeRow.append(excludeCompanyCol);
            filterContainer.appendChild(excludeRow);

            const excludeOutsourcingRow = document.createElement("div");
            excludeOutsourcingRow.style.cssText = `
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            margin-bottom: 12px;
            padding: 10px 12px;
            border-radius: 10px;
            background: rgba(255, 255, 255, 0.55);
        `;

            const excludeOutsourcingInfo = document.createElement("div");
            excludeOutsourcingInfo.style.cssText = `
            display: flex;
            flex-direction: column;
            gap: 4px;
        `;

            const excludeOutsourcingLabel = document.createElement("div");
            excludeOutsourcingLabel.textContent = "排除外包";
            excludeOutsourcingLabel.style.cssText = `
            font-size: 13px;
            font-weight: 600;
            color: var(--text-primary);
        `;

            const excludeOutsourcingHint = document.createElement("div");
            excludeOutsourcingHint.textContent = "勾选后自动过滤大多数外包、外派、驻场类公司";
            excludeOutsourcingHint.style.cssText = `
            font-size: 12px;
            color: var(--text-secondary);
        `;

            const excludeOutsourcingToggle = createToggleSwitch(
                "exclude-outsourcing",
                settings.excludeOutsourcing,
                (checked) => {
                    settings.excludeOutsourcing = checked;
                    saveSettings();
                }
            );
            elements.excludeOutsourcingToggle =
                excludeOutsourcingToggle.querySelector("input");

            excludeOutsourcingInfo.append(
                excludeOutsourcingLabel,
                excludeOutsourcingHint
            );
            excludeOutsourcingRow.append(
                excludeOutsourcingInfo,
                excludeOutsourcingToggle
            );
            filterContainer.appendChild(excludeOutsourcingRow);

            const outsourcingKeywordsRow = document.createElement("div");
            outsourcingKeywordsRow.style.cssText = `
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        `;
            outsourcingKeywordsRow.append(outsourcingKeywordsCol);
            filterContainer.appendChild(outsourcingKeywordsRow);

            const rateControlRow = document.createElement("div");
            rateControlRow.style.cssText = `
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        `;

            const rateControlCol = this._createSelectControl(
                "投递间隔：",
                "job-apply-interval",
                [
                    { value: "1000", text: "1 秒" },
                    { value: "5000", text: "5 秒" },
                    { value: "10000", text: "10 秒" },
                ]
            );
            const limitControlCol = this._createInputControl(
                "停止次数：",
                "job-apply-limit",
                "0 表示不限"
            );

            elements.jobApplyIntervalSelect = rateControlCol.querySelector("select");
            elements.jobApplyLimitInput = limitControlCol.querySelector("input");
            elements.jobApplyLimitInput.type = "number";
            elements.jobApplyLimitInput.min = "0";
            elements.jobApplyLimitInput.step = "1";
            elements.jobApplyLimitInput.value = String(getJobApplyLimit());
            elements.jobApplyIntervalSelect.value = String(getJobApplyInterval());

            elements.jobApplyIntervalSelect.addEventListener("change", (e) => {
                settings.jobApplyInterval = parseNonNegativeInt(e.target.value, 5000);
                saveSettings();
            });
            elements.jobApplyLimitInput.addEventListener("change", (e) => {
                const normalized = parseNonNegativeInt(e.target.value, 0);
                e.target.value = String(normalized);
                settings.jobApplyLimit = normalized;
                saveSettings();
            });

            rateControlRow.append(rateControlCol, limitControlCol);
            filterContainer.appendChild(rateControlRow);

            var aiRow=document.createElement("div");aiRow.style.cssText="display:flex;justify-content:space-between;align-items:center;padding:8px 0;margin-bottom:8px;";
            aiRow.innerHTML='<span style="font-size:13px;font-weight:600;color:#1e293b;">🤖 AI智能招呼语</span>';
            var aiSw=document.createElement("div");aiSw.id="ai-greeting-toggle";aiSw.style.cssText="position:relative;width:48px;height:26px;border-radius:13px;cursor:pointer;transition:background 0.3s;";
            var aiTh=document.createElement("div");aiTh.id="ai-greeting-thumb";aiTh.style.cssText="position:absolute;top:3px;width:20px;height:20px;border-radius:50%;background:white;box-shadow:0 1px 3px rgba(0,0,0,0.2);transition:left 0.3s;";
            aiSw.appendChild(aiTh);aiRow.appendChild(aiSw);
            function upAI(){var o=settings.aiGreetingEnabled;aiSw.style.background=o?"#4F46E5":"#cbd5e1";aiTh.style.left=o?"25px":"3px";var p=document.getElementById("greeting-preview-section");if(p)p.style.display=o?"block":"none";}
            setTimeout(upAI,0);
            aiSw.addEventListener("click",function(){settings.aiGreetingEnabled=!settings.aiGreetingEnabled;saveSettings();upAI();state.settings.aiGreetingEnabled=settings.aiGreetingEnabled;Core.log(settings.aiGreetingEnabled?"AI已开启-半自动":"AI已关闭-全自动");});
            filterContainer.appendChild(aiRow);
            var gp=document.createElement("div");gp.id="greeting-preview-section";gp.style.cssText="background:var(--secondary-color);border-radius:12px;padding:12px;margin-bottom:12px;";
            gp.innerHTML='<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;"><span style="font-weight:600;font-size:13px;color:#333;">📝 AI招呼语预览 (可编辑)</span><button id="regen-greeting-btn" style="padding:4px 10px;background:var(--primary-color);color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:11px;">重新生成</button></div>';
            var gta=document.createElement("textarea");gta.id="greeting-preview-text";gta.rows=5;gta.placeholder="点击职位后AI自动生成个性化招呼语...";
            gta.style.cssText="width:100%;padding:10px;border-radius:8px;border:1px solid #d1d5db;font-size:13px;resize:vertical;box-sizing:border-box;font-family:inherit;";
            gp.appendChild(gta);filterContainer.appendChild(gp);
            setTimeout(function(){var rb=document.getElementById("regen-greeting-btn");if(rb)rb.addEventListener("click",async function(){var ji=Core.extractJobDetail();if(ji&&ji.title){await Core.generateGreeting(ji);}else{Core.log("请先点击职位卡片");}});},1000);

            elements.controlBtn = this._createTextButton(
                settings.aiGreetingEnabled?"🚀 一键投递":"▶ 启动海投",
                "var(--primary-color)",
                () => {
                    toggleProcess();
                }
            );

            filterContainer.append(elements.controlBtn);
            return filterContainer;
        },

        _createInputControl(labelText, id, placeholder) {
            const controlCol = document.createElement("div");
            controlCol.style.cssText = "flex: 1;";

            const label = document.createElement("label");
            label.textContent = labelText;
            label.style.cssText =
                "display:block; margin-bottom:5px; font-weight: 500; color: #333; font-size: 0.9rem;";

            const input = document.createElement("input");
            input.id = id;
            input.placeholder = placeholder;
            input.style.cssText = `
            width: 100%;
            padding: 8px 10px;
            border-radius: 8px;
            border: 1px solid #d1d5db;
            font-size: 14px;
            box-shadow: 0 1px 2px rgba(0,0,0,0.05);
            transition: all 0.2s ease;
        `;

            controlCol.append(label, input);
            return controlCol;
        },

        _createSelectControl(labelText, id, options) {
            const controlCol = document.createElement("div");
            controlCol.style.cssText = "flex: 1;";

            const label = document.createElement("label");
            label.textContent = labelText;
            label.style.cssText =
                "display:block; margin-bottom:5px; font-weight: 500; color: #333; font-size: 0.9rem;";

            const select = document.createElement("select");
            select.id = id;
            select.style.cssText = `
            width: 100%;
            padding: 8px 10px;
            border-radius: 8px;
            border: 1px solid #d1d5db;
            font-size: 14px;
            background: white;
            color: #333;
            box-shadow: 0 1px 2px rgba(0,0,0,0.05);
            transition: all 0.2s ease;
        `;

            options.forEach((option) => {
                const opt = document.createElement("option");
                opt.value = option.value;
                opt.textContent = option.text;
                select.appendChild(opt);
            });

            controlCol.append(label, select);
            return controlCol;
        },

        _createLogger() {
            const log = document.createElement("div");
            log.id = "pro-log";
            log.className =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? "boss-joblist-log"
                    : "boss-chat-log";

            const height =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST ? "260px" : "260px";

            log.style.cssText = `
            height: ${height};
            overflow-y: auto;
            background: var(--secondary-color);
            border-radius: 12px;
            padding: 12px;
            font-size: 13px;
            line-height: 1.5;
            margin-bottom: 15px;
            margin-left: 10px;
            margin-right: 10px;
            transition: all 0.3s ease;
            user-select: text;
            scrollbar-width: thin;
            scrollbar-color: var(--primary-color) var(--secondary-color);
        `;

            log.innerHTML += `
            <style>
                #pro-log::-webkit-scrollbar {
                    width: 6px;
                }
                #pro-log::-webkit-scrollbar-track {
                    background: var(--secondary-color);
                    border-radius: 4px;
                }
                #pro-log::-webkit-scrollbar-thumb {
                    background-color: var(--primary-color);
                    border-radius: 4px;
                }
            </style>
        `;

            return log;
        },

        _createFooter() {
            const footer = document.createElement("div");
            footer.className =
                this.currentPageType === this.PAGE_TYPES.JOB_LIST
                    ? "boss-joblist-footer"
                    : "boss-chat-footer";

            footer.style.cssText = `
            text-align: center;
            font-size: 0.8em;
            color: var(--neutral-color);
            padding-top: 15px;
            border-top: 1px solid var(--accent-color);
            margin-top: auto;
            padding: 0px;
        `;

            const statsContainer = document.createElement("div");
            statsContainer.style.cssText = `
            display: flex;
            justify-content: space-around;
            margin-bottom: 15px;
        `;

            footer.append(
                statsContainer,
                document.createTextNode(`© ${new Date().getFullYear()} Zion Cai · All Rights Reserved`)
            );
            return footer;
        },

        _createTextButton(text, bgColor, onClick) {
            const btn = document.createElement("button");
            btn.className = "boss-btn";
            btn.textContent = text;
            btn.style.cssText = `
            width: 100%;
            padding: 10px 16px;
            background: ${bgColor};
            color: #fff;
            border: none;
            border-radius: 10px;
            cursor: pointer;
            font-size: 15px;
            font-weight: 500;
            transition: all 0.3s ease;
            display: flex;
            justify-content: center;
            align-items: center;
            box-shadow: 0 4px 10px rgba(0,0,0,0.1);
            transform: translateY(0px);
            margin: 0 auto;
        `;

            this._addButtonHoverEffects(btn);
            btn.addEventListener("click", onClick);

            return btn;
        },

        _createIconButton(icon,onClick,title){
            const btn=document.createElement("button");btn.className="boss-icon-btn";btn.innerHTML=icon;btn.title=title;
            btn.style.cssText="width:32px;height:32px;border-radius:50%;border:none;background:var(--accent-color);cursor:pointer;font-size:16px;display:flex;justify-content:center;align-items:center;color:var(--primary-color);overflow:hidden;";
            if(icon.includes("<svg"))btn.style.padding="4px";
            btn.addEventListener("click",onClick);
            var osf=null;if(icon.includes("<svg")){var s1=btn.querySelector("svg");if(s1){var p1=s1.querySelector("path");if(p1)osf=p1.getAttribute("fill")}}
            btn.addEventListener("mouseenter",function(){btn.style.backgroundColor="var(--primary-color)";btn.style.color="#fff";btn.style.transform="scale(1.1)";if(icon.includes("<svg")){var s2=btn.querySelector("svg");if(s2){var p2=s2.querySelector("path");if(p2)p2.setAttribute("fill","#fff")}}});
            btn.addEventListener("mouseleave",function(){btn.style.backgroundColor="var(--accent-color)";btn.style.color="var(--primary-color)";btn.style.transform="scale(1)";if(icon.includes("<svg")&&osf){var s3=btn.querySelector("svg");if(s3){var p3=s3.querySelector("path");if(p3)p3.setAttribute("fill",osf)}}});
            return btn;
        },

        _addButtonHoverEffects(btn) {
            btn.addEventListener("mouseenter", () => {
                btn.style.boxShadow = `0 6px 15px rgba(var(--primary-rgb), 0.3)`;
            });

            btn.addEventListener("mouseleave", () => {
                btn.style.boxShadow = "0 4px 10px rgba(0,0,0,0.1)";
            });
        },

        _makeDraggable(panel) {
            const header = panel.querySelector(".boss-header, .boss-chat-header");

            if (!header) return;

            header.style.cursor = "move";

            let isDragging = false;
            let startX = 0,
                startY = 0;
            let initialX = panel.offsetLeft,
                initialY = panel.offsetTop;

            header.addEventListener("mousedown", (e) => {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                initialX = panel.offsetLeft;
                initialY = panel.offsetTop;
                panel.style.transition = "none";
                panel.style.zIndex = "2147483647";
            });

            document.addEventListener("mousemove", (e) => {
                if (!isDragging) return;

                const dx = e.clientX - startX;
                const dy = e.clientY - startY;

                panel.style.left = `${initialX + dx}px`;
                panel.style.top = `${initialY + dy}px`;
                panel.style.right = "auto";
            });

            document.addEventListener("mouseup", () => {
                if (isDragging) {
                    isDragging = false;
                    panel.style.transition = "all 0.3s ease";
                    panel.style.zIndex = "2147483646";
                }
            });
        },

        createMiniIcon() {
            elements.miniIcon = document.createElement("div");
            elements.miniIcon.style.cssText = `
        width: ${CONFIG.MINI_ICON_SIZE || 48}px;
        height: ${CONFIG.MINI_ICON_SIZE || 48}px;
        position: fixed;
        bottom: 40px;
        left: 40px;
        background: var(--primary-color);
        border-radius: 50%;
        box-shadow: 0 6px 16px rgba(var(--primary-rgb), 0.4);
        cursor: pointer;
        display: none;
        justify-content: center;
        align-items: center;
        color: #fff;
        z-index: 2147483647;
        transition: all 0.3s ease;
        overflow: hidden;

    `;

            const customSvg = `
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 80" style="width:100%;height:100%;"><rect width="80" height="80" rx="14" fill="#4F46E5"/><g fill="white"><rect x="25" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="5" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="10" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="15" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="15" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="20" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="25" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="30" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="35" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="40" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="30" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="45" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="45" width="4.7" height="4.7" rx="0.5"/><rect x="25" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="50" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="50" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="20" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="55" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="55" width="4.7" height="4.7" rx="0.5"/><rect x="10" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="15" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="35" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="40" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="60" y="60" width="4.7" height="4.7" rx="0.5"/><rect x="65" y="60" width="4.7" height="4.7" rx="0.5"/></g></svg>
    `;

            elements.miniIcon.innerHTML = customSvg;

            elements.miniIcon.addEventListener("mouseenter", () => {
                elements.miniIcon.style.transform = "scale(1.1)";
                elements.miniIcon.style.boxShadow = `0 8px 20px rgba(var(--primary-rgb), 0.5)`;
            });

            elements.miniIcon.addEventListener("mouseleave", () => {
                elements.miniIcon.style.transform = "scale(1)";
                elements.miniIcon.style.boxShadow = `0 6px 16px rgba(var(--primary-rgb), 0.4)`;
            });

            elements.miniIcon.addEventListener("click", () => {
                state.isMinimized = false;
                elements.panel.style.transform = "translateY(0)";
                elements.miniIcon.style.display = "none";
            });

            document.body.appendChild(elements.miniIcon);
        },

        _hexToRgb(hex) {
            hex = hex.replace("#", "");

            const r = parseInt(hex.substring(0, 2), 16);
            const g = parseInt(hex.substring(2, 4), 16);
            const b = parseInt(hex.substring(4, 6), 16);

            return `${r}, ${g}, ${b}`;
        },
    };

    const settings = {
        useAutoSendResume: JSON.parse(
            localStorage.getItem("useAutoSendResume") || "false"
        ),
        aiGreetingEnabled: JSON.parse(
            localStorage.getItem("aiGreetingEnabled") || "true"
        ),
        actionDelays: {
            click: parseInt(localStorage.getItem("clickDelay") || "130"),
        },
        jobApplyInterval: parseInt(
            localStorage.getItem("jobApplyInterval") || "5000",
            10
        ),
        jobApplyLimit: parseInt(
            localStorage.getItem("jobApplyLimit") || "0",
            10
        ),
        ai: {
            apiUrl: localStorage.getItem("aiApiUrl") || "",
            apiKey: localStorage.getItem("aiApiKey") || "",
            model: localStorage.getItem("aiModel") || "",
            role:
                localStorage.getItem("aiRole") ||
                "你是求职的应届生正在面对HR，回复需满足：20字内，编造专业对口/实习经验/证书任一岗位优势；被问个人信息或岗位条件，直接配合提供合理数据；全程积极真诚无拒绝言辞。",
        },
        resume: localStorage.getItem("userResume") || "",
        greetingTemplate: localStorage.getItem("greetingTemplate") || "",

        autoReply: JSON.parse(localStorage.getItem("autoReply") || "false"),

        useAutoSendImageResume: JSON.parse(
            localStorage.getItem("useAutoSendImageResume") || "false"
        ),
        imageResumeData: localStorage.getItem("imageResumeData") || null,
        imageResumes: getStoredJSON("imageResumes", []),

        communicationSyncEnabled: JSON.parse(
            localStorage.getItem("communicationSyncEnabled") || "true"
        ),
        communicationBaseUrl:
            localStorage.getItem("communicationBaseUrl") || "http://127.0.0.1:8080",
        communicationToken: localStorage.getItem("communicationToken") || "",
        communicationIncludeKeywords:
            localStorage.getItem("communicationIncludeKeywords") || "",
        communicationMode: localStorage.getItem("communicationMode") || "new-only",

        recruiterActivityStatus: JSON.parse(
            localStorage.getItem("recruiterActivityStatus") || '["不限"]'
        ),

        excludeOutsourcing: JSON.parse(
            localStorage.getItem("excludeOutsourcing") || "false"
        ),

        outsourcingKeywords: getStoredJSON(
            "outsourcingKeywords",
            CONFIG.DEFAULT_OUTSOURCING_KEYWORDS
        ),

        excludeHeadhunters: JSON.parse(
            localStorage.getItem("excludeHeadhunters") || "false"
        ),
    };

    function saveSettings() {
        localStorage.setItem(
            "useAutoSendResume",
            settings.useAutoSendResume.toString()
        );
        localStorage.setItem(
            "aiGreetingEnabled",
            settings.aiGreetingEnabled.toString()
        );
        localStorage.setItem("clickDelay", settings.actionDelays.click.toString());
        localStorage.setItem(
            "jobApplyInterval",
            getJobApplyInterval().toString()
        );
        localStorage.setItem(
            "jobApplyLimit",
            getJobApplyLimit().toString()
        );
        localStorage.setItem("aiRole", settings.ai.role);
        localStorage.setItem("aiApiUrl", settings.ai.apiUrl||"");
        localStorage.setItem("aiApiKey", settings.ai.apiKey||"");
        localStorage.setItem("aiModel", settings.ai.model||"");
        localStorage.setItem(
            "communicationSyncEnabled",
            Boolean(settings.communicationSyncEnabled).toString()
        );
        localStorage.setItem(
            "communicationBaseUrl",
            settings.communicationBaseUrl || ""
        );
        localStorage.setItem(
            "communicationToken",
            settings.communicationToken || ""
        );
        localStorage.setItem(
            "communicationIncludeKeywords",
            settings.communicationIncludeKeywords || ""
        );
        if(settings.resume!==undefined)localStorage.setItem("userResume",settings.resume);
        if(settings.greetingTemplate!==undefined)localStorage.setItem("greetingTemplate",settings.greetingTemplate);

        localStorage.setItem("autoReply", settings.autoReply.toString());

        localStorage.setItem(
            "useAutoSendImageResume",
            settings.useAutoSendImageResume.toString()
        );

        if (settings.imageResumes) {
            localStorage.setItem(
                "imageResumes",
                JSON.stringify(settings.imageResumes)
            );
        }

        if (settings.imageResumeData) {
            localStorage.setItem("imageResumeData", settings.imageResumeData);
        } else {
            localStorage.removeItem("imageResumeData");
        }

        localStorage.setItem(
            "recruiterActivityStatus",
            JSON.stringify(settings.recruiterActivityStatus)
        );

        localStorage.setItem(
            "excludeOutsourcing",
            settings.excludeOutsourcing.toString()
        );

        localStorage.setItem(
            "outsourcingKeywords",
            JSON.stringify(settings.outsourcingKeywords || [])
        );

        localStorage.setItem(
            "excludeHeadhunters",
            settings.excludeHeadhunters.toString()
        );

        if (state.settings) {
            Object.assign(state.settings, settings);
        }
    }

    function parseNonNegativeInt(value, fallback = 0) {
        const parsed = Number.parseInt(String(value ?? "").trim(), 10);
        return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
    }

    function getJobApplyInterval() {
        const value = parseNonNegativeInt(settings.jobApplyInterval, 5000);
        return [1000, 5000, 10000].includes(value) ? value : 5000;
    }

    function getJobApplyLimit() {
        return parseNonNegativeInt(settings.jobApplyLimit, 0);
    }

    function normalizeAiApiUrl(rawUrl) {
        const input = (rawUrl || "").trim();

        if (!input) {
            return "";
        }

        try {
            const normalizedInput = /^https?:\/\//i.test(input)
                ? input
                : `https://${input}`;
            const url = new URL(normalizedInput);
            const path = url.pathname.replace(/\/+$/, "");

            if (!path || path === "/") {
                url.pathname = "/v1/chat/completions";
            } else if (/^\/v\d+$/i.test(path)) {
                url.pathname = `${path}/chat/completions`;
            } else if (!/\/(chat\/completions|responses)$/i.test(path)) {
                url.pathname = path;
            }

            return url.toString();
        } catch (error) {
            return "";
        }
    }

    function readAiConfigFromInputs() {
        const apiUrlInput = document.getElementById("ai-api-url-input");
        const apiKeyInput = document.getElementById("ai-api-key-input");
        const modelInput = document.getElementById("ai-model-input");

        return {
            apiUrl: (apiUrlInput?.value || "").trim(),
            apiKey: (apiKeyInput?.value || "").trim(),
            model: (modelInput?.value || "").trim(),
        };
    }

    function applyAiConfig(config, persist = true) {
        settings.ai.apiUrl = config.apiUrl;
        settings.ai.apiKey = config.apiKey;
        settings.ai.model = config.model || "";

        if (state.settings?.ai) {
            state.settings.ai.apiUrl = settings.ai.apiUrl;
            state.settings.ai.apiKey = settings.ai.apiKey;
            state.settings.ai.model = settings.ai.model;
        }

        if (persist) {
            saveSettings();
        }
    }

    function setAiApiTestStatus(message, type = "info") {
        const result = document.getElementById("ai-api-test-result");
        if (!result) return;

        const palette = {
            info: {
                background: "#eff6ff",
                border: "#bfdbfe",
                color: "#1d4ed8",
            },
            success: {
                background: "#ecfdf5",
                border: "#86efac",
                color: "#166534",
            },
            error: {
                background: "#fef2f2",
                border: "#fecaca",
                color: "#b91c1c",
            },
        };

        const current = palette[type] || palette.info;
        result.style.display = "block";
        result.style.background = current.background;
        result.style.borderColor = current.border;
        result.style.color = current.color;
        result.textContent = message;
    }

    function readCommunicationConfigFromInputs() {
        const baseUrlInput = document.getElementById("communication-base-url-input");
        const tokenInput = document.getElementById("communication-token-input");
        const enabledInput = document.getElementById("communication-sync-enabled-input");

        return {
            communicationBaseUrl:
                (baseUrlInput?.value || "").trim() || "http://127.0.0.1:8080",
            communicationToken: (tokenInput?.value || "").trim(),
            communicationSyncEnabled: !!enabledInput?.checked,
        };
    }

    function applyCommunicationConfig(config, persist = true) {
        settings.communicationBaseUrl = config.communicationBaseUrl || "";
        settings.communicationToken = config.communicationToken || "";
        settings.communicationSyncEnabled = !!config.communicationSyncEnabled;

        if (state.settings) {
            state.settings.communicationBaseUrl = settings.communicationBaseUrl;
            state.settings.communicationToken = settings.communicationToken;
            state.settings.communicationSyncEnabled =
                settings.communicationSyncEnabled;
        }

        if (persist) {
            saveSettings();
        }
    }

    function setCommunicationTestStatus(message, type = "info") {
        const result = document.getElementById("communication-test-result");
        if (!result) return;

        const palette = {
            info: {
                background: "#eff6ff",
                border: "#bfdbfe",
                color: "#1d4ed8",
            },
            success: {
                background: "#ecfdf5",
                border: "#86efac",
                color: "#166534",
            },
            error: {
                background: "#fef2f2",
                border: "#fecaca",
                color: "#b91c1c",
            },
        };

        const current = palette[type] || palette.info;
        result.style.display = "block";
        result.style.background = current.background;
        result.style.borderColor = current.border;
        result.style.color = current.color;
        result.textContent = message;
    }

    function deriveBridgeOrigins(baseUrl) {
        const origins = new Set();
        origins.add("http://127.0.0.1:5173");
        origins.add("http://localhost:5173");

        try {
            const parsed = new URL(baseUrl || "http://127.0.0.1:8080");
            if (parsed.port === "8080") {
                origins.add(`${parsed.protocol}//${parsed.hostname}:5173`);
            } else {
                origins.add(parsed.origin);
            }
        } catch (error) {
            console.warn("deriveBridgeOrigins failed:", error);
        }

        return Array.from(origins);
    }

    async function requestTokenFromBridgeOrigin(origin) {
        const iframe = document.createElement("iframe");
        const requestId = `bridge-${Date.now()}-${Math.random().toString(16).slice(2)}`;
        iframe.style.display = "none";
        iframe.src = `${origin}/token-bridge.html`;

        return new Promise((resolve, reject) => {
            let settled = false;
            let timeoutId = null;

            const cleanup = () => {
                window.removeEventListener("message", onMessage);
                iframe.removeEventListener("load", onLoad);
                iframe.remove();
                if (timeoutId) {
                    clearTimeout(timeoutId);
                }
            };

            const finish = (handler, value) => {
                if (settled) {
                    return;
                }
                settled = true;
                cleanup();
                handler(value);
            };

            const onMessage = (event) => {
                if (event.origin !== origin) {
                    return;
                }
                const data = event.data || {};
                if (
                    data.type !== CONFIG.TOKEN_BRIDGE.RESPONSE_TYPE ||
                    data.requestId !== requestId
                ) {
                    return;
                }
                finish(resolve, data.payload || {});
            };

            const onLoad = () => {
                try {
                    iframe.contentWindow?.postMessage(
                        {
                            type: CONFIG.TOKEN_BRIDGE.REQUEST_TYPE,
                            requestId,
                        },
                        origin
                    );
                } catch (error) {
                    finish(reject, error);
                }
            };

            timeoutId = setTimeout(() => {
                finish(reject, new Error(`bridge timeout: ${origin}`));
            }, CONFIG.TOKEN_BRIDGE.TIMEOUT);

            window.addEventListener("message", onMessage);
            iframe.addEventListener("load", onLoad);
            document.body.appendChild(iframe);
        });
    }

    function openCommunicationAuthWindow(baseUrl) {
        const origins = deriveBridgeOrigins(baseUrl);
        const origin = origins[0] || "http://127.0.0.1:5173";
        const requestId = `bridge-auth-${Date.now()}-${Math.random().toString(16).slice(2)}`;
        const bridgeUrl = `${origin}/token-bridge.html?requestId=${encodeURIComponent(requestId)}&redirect=${encodeURIComponent("/token-bridge.html")}`;

        return new Promise((resolve, reject) => {
            const authWindow = window.open(
                bridgeUrl,
                "log-analysis-token-bridge",
                "width=520,height=720"
            );

            if (!authWindow) {
                reject(new Error("浏览器拦截了登录弹窗，请允许弹窗后重试"));
                return;
            }

            let timeoutId = null;

            const cleanup = () => {
                window.removeEventListener("message", onMessage);
                if (timeoutId) {
                    clearTimeout(timeoutId);
                }
            };

            const onMessage = (event) => {
                const data = event.data || {};
                if (
                    data.type !== CONFIG.TOKEN_BRIDGE.RESPONSE_TYPE ||
                    data.requestId !== requestId
                ) {
                    return;
                }

                const accessToken = String(data.payload?.accessToken || "").trim();
                cleanup();

                if (!accessToken) {
                    reject(new Error("未登录或登录已过期"));
                    return;
                }

                resolve({
                    accessToken,
                    origin: event.origin,
                });
            };

            timeoutId = setTimeout(() => {
                cleanup();
                reject(new Error("授权超时，请完成登录后重试"));
            }, 120000);

            window.addEventListener("message", onMessage);
        });
    }

    function createSettingsDialog() {
        const dialog = document.createElement("div");
        dialog.id = "boss-settings-dialog";
        dialog.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: clamp(300px, 90vw, 550px);
        height: 80vh;
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 10px 30px rgba(0,0,0,0.15);
        z-index: 999999;
        display: none;
        flex-direction: column;
        font-family: 'Segoe UI', sans-serif;
        overflow: hidden;
        transition: all 0.3s ease;
    `;

        dialog.innerHTML += `
        <style>
            #boss-settings-dialog {
                opacity: 0;
                transform: translate(-50%, -50%) scale(0.95);
            }
            #boss-settings-dialog.active {
                opacity: 1;
                transform: translate(-50%, -50%) scale(1);
            }
            .setting-item {
                transition: all 0.2s ease;
            }
            .setting-item:hover {
                background-color: rgba(0, 123, 255, 0.05);
            }
            .multi-select-container {
                position: relative;
                width: 100%;
                margin-top: 10px;
            }
            .multi-select-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 10px;
                border-radius: 8px;
                border: 1px solid #d1d5db;
                background: white;
                cursor: pointer;
                transition: all 0.2s ease;
            }
            .multi-select-header:hover {
                border-color: rgba(0, 123, 255, 0.7);
            }
            .multi-select-options {
                position: absolute;
                top: 100%;
                left: 0;
                right: 0;
                max-height: 200px;
                overflow-y: auto;
                border-radius: 8px;
                border: 1px solid #d1d5db;
                background: white;
                z-index: 100;
                box-shadow: 0 4px 10px rgba(0,0,0,0.1);
                display: none;
            }
            .multi-select-option {
                padding: 10px;
                cursor: pointer;
                transition: all 0.2s ease;
            }
            .multi-select-option:hover {
                background-color: rgba(0, 123, 255, 0.05);
            }
            .multi-select-option.selected {
                background-color: rgba(0, 123, 255, 0.1);
            }
            .multi-select-clear {
                color: #666;
                cursor: pointer;
                margin-left: 5px;
            }
            .multi-select-clear:hover {
                color: #333;
            }
        </style>
    `;

        const dialogHeader = createDialogHeader("海投助手-设置");

        const dialogContent = document.createElement("div");
        dialogContent.style.cssText = `
        padding: 18px;
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: rgba(0, 123, 255, 0.5) rgba(0, 0, 0, 0.05);
    `;

        dialogContent.innerHTML += `
    <style>
        #boss-settings-dialog ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        #boss-settings-dialog ::-webkit-scrollbar-track {
            background: rgba(0,0,0,0.05);
            border-radius: 10px;
            margin: 8px 0;
        }
        #boss-settings-dialog ::-webkit-scrollbar-thumb {
            background: rgba(0, 123, 255, 0.5);
            border-radius: 10px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            transition: all 0.2s ease;
        }
        #boss-settings-dialog ::-webkit-scrollbar-thumb:hover {
            background: rgba(0, 123, 255, 0.7);
            box-shadow: 0 1px 5px rgba(0,0,0,0.15);
        }
    </style>
    `;

        const tabsContainer = document.createElement("div");
        tabsContainer.style.cssText = `
        display: flex;
        border-bottom: 1px solid rgba(0, 123, 255, 0.2);
        margin-bottom: 20px;
    `;

        const aiTab = document.createElement("button");
        aiTab.textContent = "聊天设置";
        aiTab.className = "settings-tab active";
        aiTab.style.cssText = `
        padding: 9px 15px;
        background: rgba(0, 123, 255, 0.9);
        color: white;
        border: none;
        border-radius: 8px 8px 0 0;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
        margin-right: 5px;
    `;

        const advancedTab = document.createElement("button");
        advancedTab.textContent = "高级设置";
        advancedTab.className = "settings-tab";
        advancedTab.style.cssText = `
        padding: 9px 15px;
        background: rgba(0, 0, 0, 0.05);
        color: #333;
        border: none;
        border-radius: 8px 8px 0 0;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
        margin-right: 5px;
    `;

        tabsContainer.append(aiTab, advancedTab);

        const aiSettingsPanel = document.createElement("div");
        aiSettingsPanel.id = "ai-settings-panel";
        var as=document.createElement("div");as.style.cssText="background:#f8fafc;border-radius:12px;padding:15px;margin-bottom:15px;border:1px solid #e2e8f0;";as.appendChild(Object.assign(document.createElement("h4"),{textContent:"AI API 配置",style:{margin:"0 0 12px",color:"#1e293b",fontSize:"15px",fontWeight:"600"}}));[{id:"ai-api-url-input",l:"API 地址",p:"请手动输入 API 地址"},{id:"ai-api-key-input",l:"API Key",p:"请手动输入 sk-...",pw:!0},{id:"ai-model-input",l:"model_name",p:"请手动输入模型名"}].forEach(function(cf){as.appendChild(Object.assign(document.createElement("label"),{textContent:cf.l,style:{display:"block",marginBottom:"4px",fontSize:"12px",color:"#64748b"}}));var ip=Object.assign(document.createElement("input"),{type:cf.pw?"password":"text",id:cf.id,placeholder:cf.p});ip.style.cssText="width:100%;padding:8px 10px;border-radius:6px;border:1px solid #d1d5db;font-size:13px;margin-bottom:10px;box-sizing:border-box;";as.appendChild(ip);});var aw=Object.assign(document.createElement("div"),{textContent:"API 地址、Key、model_name 都需手动填写。若只填域名，脚本会自动补齐到聊天接口。",style:{fontSize:"11px",color:"#f59e0b",marginBottom:"10px"}});as.appendChild(aw);var abr=document.createElement("div");abr.style.cssText="display:flex;gap:10px;margin-top:4px;";var ab=document.createElement("button");ab.textContent="保存API配置";ab.style.cssText="flex:1;padding:8px;background:#4F46E5;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;";ab.addEventListener("click",function(){try{var config=readAiConfigFromInputs();if(!config.apiUrl){throw new Error('请先填写 API 地址');}if(!config.apiKey){throw new Error('请先填写 API Key');}if(!config.model){throw new Error('请先填写 model_name');}config.apiUrl=normalizeAiApiUrl(config.apiUrl);if(!config.apiUrl){throw new Error('API 地址格式不正确');}applyAiConfig(config,true);var a=document.getElementById('ai-api-url-input');if(a)a.value=config.apiUrl;setAiApiTestStatus('API 配置已保存', 'success');showNotification('API已保存');}catch(error){setAiApiTestStatus('保存失败：'+error.message, 'error');showNotification('保存失败: '+error.message, 'error');}});var tb=document.createElement("button");tb.textContent="测试连接";tb.style.cssText="flex:1;padding:8px;background:#0f766e;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;";tb.addEventListener("click",async function(){try{var config=readAiConfigFromInputs();if(!config.apiUrl){throw new Error('请先填写 API 地址');}if(!config.apiKey){throw new Error('请先填写 API Key');}if(!config.model){throw new Error('请先填写 model_name');}config.apiUrl=normalizeAiApiUrl(config.apiUrl);if(!config.apiUrl){throw new Error('API 地址格式不正确');}applyAiConfig(config,false);var a=document.getElementById('ai-api-url-input');if(a)a.value=config.apiUrl;setAiApiTestStatus('正在测试 AI 接口...', 'info');tb.disabled=true;tb.style.opacity='0.7';var reply=await Core.requestAi('请回复“连接成功”四个字。','你是接口联调助手，只能回复“连接成功”四个字。');setAiApiTestStatus('测试成功：'+reply, 'success');showNotification('AI 测试成功');}catch(error){setAiApiTestStatus('测试失败：'+error.message, 'error');showNotification('AI 测试失败: '+error.message, 'error');}finally{tb.disabled=false;tb.style.opacity='1';}});abr.append(ab,tb);as.appendChild(abr);var atr=Object.assign(document.createElement("div"),{id:"ai-api-test-result",textContent:"填写 API 地址、Key、model_name 后可点“测试连接”验证接口是否可用",style:{display:"block",marginTop:"10px",padding:"10px 12px",borderRadius:"8px",border:"1px solid #bfdbfe",background:"#eff6ff",color:"#1d4ed8",fontSize:"12px",lineHeight:"1.5"}});as.appendChild(atr);aiSettingsPanel.appendChild(as);
        var cs=document.createElement("div");cs.style.cssText="background:#f8fafc;border-radius:12px;padding:15px;margin-bottom:15px;border:1px solid #e2e8f0;";cs.appendChild(Object.assign(document.createElement("h4"),{textContent:"岗位沟通同步",style:{margin:"0 0 12px",color:"#1e293b",fontSize:"15px",fontWeight:"600"}}));[{id:"communication-base-url-input",l:"后端地址",p:"默认：http://127.0.0.1:8080"},{id:"communication-token-input",l:"Access Token",p:"优先自动授权获取，失败时可手填",pw:!0}].forEach(function(cf){cs.appendChild(Object.assign(document.createElement("label"),{textContent:cf.l,style:{display:"block",marginBottom:"4px",fontSize:"12px",color:"#64748b"}}));var ip=Object.assign(document.createElement("input"),{type:cf.pw?"password":"text",id:cf.id,placeholder:cf.p});ip.style.cssText="width:100%;padding:8px 10px;border-radius:6px;border:1px solid #d1d5db;font-size:13px;margin-bottom:10px;box-sizing:border-box;";cs.appendChild(ip);});var ctl=document.createElement("label");ctl.style.cssText="display:flex;align-items:center;gap:8px;font-size:13px;color:#334155;margin-top:4px;";var ccb=Object.assign(document.createElement("input"),{type:"checkbox",id:"communication-sync-enabled-input"});ctl.appendChild(ccb);ctl.appendChild(document.createTextNode("启用数据库同步与已回复跳过"));cs.appendChild(ctl);var ch=Object.assign(document.createElement("div"),{textContent:"启用后，脚本会在沟通成功后写入后端，并在下次运行前跳过已回复岗位。默认地址为 http://127.0.0.1:8080。",style:{fontSize:"11px",color:"#64748b",marginTop:"8px",lineHeight:"1.5"}});cs.appendChild(ch);var cbr=document.createElement("div");cbr.style.cssText="display:flex;gap:10px;margin-top:12px;flex-wrap:wrap;";var cab=document.createElement("button");cab.textContent="去后台登录并授权";cab.style.cssText="flex:1;min-width:160px;padding:8px;background:#7c3aed;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;";cab.addEventListener("click",async function(){try{var config=readCommunicationConfigFromInputs();applyCommunicationConfig(config,false);setCommunicationTestStatus('正在打开后台登录授权页...', 'info');cab.disabled=true;cab.style.opacity='0.7';var result=await openCommunicationAuthWindow(config.communicationBaseUrl);settings.communicationToken=result.accessToken;if(state.settings){state.settings.communicationToken=result.accessToken;}var tokenInput=document.getElementById('communication-token-input');if(tokenInput)tokenInput.value=result.accessToken;saveSettings();setCommunicationTestStatus('授权成功，已自动填充 Access Token', 'success');showNotification('后台授权成功');}catch(error){setCommunicationTestStatus('授权失败：'+error.message, 'error');showNotification('授权失败: '+error.message, 'error');}finally{cab.disabled=false;cab.style.opacity='1';}});var ctb=document.createElement("button");ctb.textContent="测试连接";ctb.style.cssText="flex:1;min-width:120px;padding:8px;background:#0f766e;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;";ctb.addEventListener("click",async function(){try{var config=readCommunicationConfigFromInputs();if(!config.communicationSyncEnabled){throw new Error('请先勾选启用数据库同步');}if(!config.communicationBaseUrl){throw new Error('请先填写后端地址');}applyCommunicationConfig(config,false);setCommunicationTestStatus('正在测试岗位沟通接口...', 'info');ctb.disabled=true;ctb.style.opacity='0.7';var overview=await Core.requestCommunicationApi('/api/job-communications/overview','GET');var today=overview&&overview.todayCommunicated!==undefined?overview.todayCommunicated:'未知';setCommunicationTestStatus('连接成功，今日沟通数：'+today, 'success');showNotification('岗位沟通接口连接成功');}catch(error){setCommunicationTestStatus('连接失败：'+error.message, 'error');showNotification('连接失败: '+error.message, 'error');}finally{ctb.disabled=false;ctb.style.opacity='1';}});var cwb=document.createElement("button");cwb.textContent="测试写入";cwb.style.cssText="flex:1;min-width:120px;padding:8px;background:#2563eb;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;font-weight:500;";cwb.addEventListener("click",async function(){try{var config=readCommunicationConfigFromInputs();if(!config.communicationSyncEnabled){throw new Error('请先勾选启用数据库同步');}if(!config.communicationBaseUrl){throw new Error('请先填写后端地址');}applyCommunicationConfig(config,false);setCommunicationTestStatus('正在写入测试记录...', 'info');cwb.disabled=true;cwb.style.opacity='0.7';var testJobId='boss-script-test-'+Date.now();await Core.requestCommunicationApi('/api/job-communications/upsert','POST',{platform:'BOSS',jobId:testJobId,jobTitle:'脚本联通测试岗位',companyName:'脚本联通测试公司',jobLocation:'Wuhan',salaryRange:'10k-15k',jobUrl:location.href,hrName:'脚本测试HR',hrKey:'script-test-hr',sourcePayload:JSON.stringify({type:'manual-test',createdAt:new Date().toISOString()})});setCommunicationTestStatus('写入成功，测试岗位ID：'+testJobId, 'success');showNotification('测试写入成功');}catch(error){setCommunicationTestStatus('写入失败：'+error.message, 'error');showNotification('写入失败: '+error.message, 'error');}finally{cwb.disabled=false;cwb.style.opacity='1';}});cbr.append(cab,ctb,cwb);cs.appendChild(cbr);var ctr=Object.assign(document.createElement("div"),{id:"communication-test-result",textContent:"推荐先点“去后台登录并授权”。授权成功后，脚本会自动保存 Access Token。",style:{display:"block",marginTop:"10px",padding:"10px 12px",borderRadius:"8px",border:"1px solid #bfdbfe",background:"#eff6ff",color:"#1d4ed8",fontSize:"12px",lineHeight:"1.5"}});cs.appendChild(ctr);aiSettingsPanel.appendChild(cs);


        const roleSettingResult = createSettingItem(
            "AI角色定位",
            "定义AI在对话中的角色和语气特点",
            () => document.getElementById("ai-role-input")
        );

        const roleSetting = roleSettingResult.settingItem;

        const roleInput = document.createElement("textarea");
        roleInput.id = "ai-role-input";
        roleInput.rows = 5;
        roleInput.style.cssText = `
        width: 100%;
        padding: 12px;
        border-radius: 8px;
        border: 1px solid #d1d5db;
        resize: vertical;
        font-size: 14px;
        transition: all 0.2s ease;
        margin-top: 10px;
        opacity: 1;
        pointer-events: auto;
    `;

        addFocusBlurEffects(roleInput);
        roleSetting.append(roleInput);
        aiSettingsPanel.append(roleSetting);

        const greetingsSettingResult = createSettingItem(
            "自我介绍",
            "首次沟通时依次发送的自我介绍内容",
            () => document.getElementById("greetings-container")
        );

        const greetingsSetting = greetingsSettingResult.settingItem;
        const greetingsContainer = document.createElement("div");
        greetingsContainer.id = "greetings-container";
        greetingsContainer.style.cssText = `
        width: 100%;
        margin-top: 10px;
    `;

        const greetingsList = document.createElement("div");
        greetingsList.id = "greetings-list";
        greetingsList.style.cssText = `
        max-height: 200px;
        overflow-y: auto;
        margin-bottom: 10px;
        border: 1px solid #d1d5db;
        border-radius: 8px;
        padding: 10px;
    `;

        const addGreetingBtn = document.createElement("button");
        addGreetingBtn.textContent = "添加自我介绍";
        addGreetingBtn.style.cssText = `
        padding: 6px 12px;
        border-radius: 4px;
        border: 1px solid rgba(0, 123, 255, 0.7);
        background: rgba(0, 123, 255, 0.1);
        color: rgba(0, 123, 255, 0.9);
        cursor: pointer;
        font-size: 13px;
        transition: all 0.2s ease;
        width: 100%;
        margin-top: 8px;
    `;

        addGreetingBtn.addEventListener("mouseenter", () => {
            addGreetingBtn.style.backgroundColor = "rgba(0, 123, 255, 0.2)";
        });

        addGreetingBtn.addEventListener("mouseleave", () => {
            addGreetingBtn.style.backgroundColor = "rgba(0, 123, 255, 0.1)";
        });

        addGreetingBtn.addEventListener("click", () => {
            addGreetingItem();
        });

        greetingsContainer.append(greetingsList, addGreetingBtn);
        greetingsSetting.append(greetingsContainer);

        var rr=createSettingItem("个人简历","输入教育背景、技能栈等，AI据此生成个性化招呼语",function(){return document.getElementById("user-resume-input");});var rs=rr.settingItem;var ri=document.createElement("textarea");ri.id="user-resume-input";ri.rows=5;ri.placeholder="我叫Zion Cai，3年前端经验...";ri.style.cssText="width:100%;padding:12px;border-radius:8px;border:1px solid #d1d5db;resize:vertical;font-size:14px;margin-top:10px;box-sizing:border-box;";rs.appendChild(ri);aiSettingsPanel.appendChild(rs);var tr=createSettingItem("招呼语模板","使用 {resume} {job_title} {job_requirements} 占位符",function(){return document.getElementById("greeting-template-input");});var ts=tr.settingItem;var ti=document.createElement("textarea");ti.id="greeting-template-input";ti.rows=6;ti.placeholder="根据以下信息生成3-5条简短的打招呼消息...";ti.style.cssText="width:100%;padding:12px;border-radius:8px;border:1px solid #d1d5db;resize:vertical;font-size:14px;margin-top:10px;box-sizing:border-box;";ts.appendChild(ti);aiSettingsPanel.appendChild(ts);
        var ir=createSettingItem("📤 导入简历 (Word/PDF/TXT)","支持 .docx .pdf .txt",function(){return document.getElementById("resume-file-input");});
        var ist=ir.settingItem;
        var iz=document.createElement("div");iz.style.cssText="border:2px dashed #cbd5e1;border-radius:10px;padding:16px;text-align:center;cursor:pointer;margin-top:8px;";
        iz.innerHTML='<div style="font-size:28px;">📂</div><div style="font-size:13px;color:#64748b;">点击上传简历文件</div><div style="font-size:11px;color:#94a3b8;">.docx .pdf .txt 最大5MB</div>';
        var fi=document.createElement("input");fi.type="file";fi.id="resume-file-input";fi.accept=".docx,.pdf,.txt";fi.style.display="none";
        var pd=document.createElement("div");pd.id="parsed-resume-result";pd.style.cssText="display:none;background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:10px 12px;margin-top:10px;font-size:12px;color:#166534;";
        iz.addEventListener("click",function(){fi.click();});
        iz.addEventListener("dragover",function(e){e.preventDefault();iz.style.borderColor="#4F46E5";iz.style.background="#f5f3ff";});
        iz.addEventListener("dragleave",function(){iz.style.borderColor="#cbd5e1";iz.style.background="";});
        iz.addEventListener("drop",function(e){e.preventDefault();iz.style.borderColor="#cbd5e1";iz.style.background="";if(e.dataTransfer.files[0])prf(e.dataTransfer.files[0]);});
        fi.addEventListener("change",function(e){if(e.target.files[0])prf(e.target.files[0]);});
        function prf(file){if(file.size>5242880){alert("文件不能超过5MB");return;}
            var ext=file.name.split(".").pop().toLowerCase();var rdr=new FileReader();
            rdr.onload=function(e){var t="";
                if(ext==="txt"){t=e.target.result;}
                else if(ext==="docx"){try{var arr=new Uint8Array(e.target.result);var raw=new TextDecoder("utf-8").decode(arr);
                    var parts=raw.split("<w:t>");var txtParts=[];for(var i=1;i<parts.length;i++){var end=parts[i].indexOf("<");if(end>0)txtParts.push(parts[i].substring(0,end));}
                    t=txtParts.join("");if(!t)t="无法解析";}catch(err){t="解析失败: "+err.message;}}
                else if(ext==="pdf"){t="PDF需额外库支持，建议另存为.txt后导入";}
                if(t&&t.trim()){var ru=document.getElementById("user-resume-input");if(ru)ru.value=t.trim();pd.style.display="block";pd.innerHTML="<strong>✅ 已识别: "+file.name+"</strong>";iz.style.borderColor="#86efac";iz.style.background="#f0fdf4";}else{alert("未识别出文本内容");}};
            ext==="docx"?rdr.readAsArrayBuffer(file):rdr.readAsText(file);}
        ist.appendChild(iz);ist.appendChild(fi);ist.appendChild(pd);aiSettingsPanel.appendChild(ist);

        aiSettingsPanel.append(greetingsSetting);

        const advancedSettingsPanel = document.createElement("div");
        advancedSettingsPanel.id = "advanced-settings-panel";
        advancedSettingsPanel.style.display = "none";

        const autoReplySettingResult = createSettingItem(
            "Ai回复模式",
            "开启后Ai将自动回复消息",
            () => document.querySelector("#toggle-auto-reply-mode input")
        );

        const autoReplySetting = autoReplySettingResult.settingItem;
        const autoReplyDescriptionContainer =
            autoReplySettingResult.descriptionContainer;

        const autoReplyToggle = createToggleSwitch(
            "auto-reply-mode",
            settings.autoReply,
            (checked) => {
                settings.autoReply = checked;
            },
            true
        );

        autoReplyDescriptionContainer.append(autoReplyToggle);

        const autoSendResumeSettingResult = createSettingItem(
            "自动发送附件简历",
            "开启后系统将自动发送附件简历给HR",
            () => document.querySelector("#toggle-auto-send-resume input")
        );

        const autoSendResumeSetting = autoSendResumeSettingResult.settingItem;
        const autoSendResumeDescriptionContainer =
            autoSendResumeSettingResult.descriptionContainer;

        const autoSendResumeToggle = createToggleSwitch(
            "auto-send-resume",
            settings.useAutoSendResume,
            (checked) => {
                settings.useAutoSendResume = checked;
            },
            true
        );

        autoSendResumeDescriptionContainer.append(autoSendResumeToggle);

        const excludeHeadhuntersSettingResult = createSettingItem(
            "投递时排除猎头",
            "开启后将不会向猎头职位自动投递简历",
            () => document.querySelector("#toggle-exclude-headhunters input")
        );

        const excludeHeadhuntersSetting =
            excludeHeadhuntersSettingResult.settingItem;
        const excludeHeadhuntersDescriptionContainer =
            excludeHeadhuntersSettingResult.descriptionContainer;

        const excludeHeadhuntersToggle = createToggleSwitch(
            "exclude-headhunters",
            settings.excludeHeadhunters,
            (checked) => {
                settings.excludeHeadhunters = checked;
            },
            true
        );

        excludeHeadhuntersDescriptionContainer.append(excludeHeadhuntersToggle);

        const imageResumeSettingResult = createSettingItem(
            "发送图片简历",
            "首次沟通发送图片简历（需先选择JPG格式图片）",
            () => document.querySelector("#toggle-auto-send-image-resume input")
        );

        const imageResumeSetting = imageResumeSettingResult.settingItem;
        const imageResumeDescriptionContainer =
            imageResumeSettingResult.descriptionContainer;

        if (!state.settings.imageResumes) {
            state.settings.imageResumes = [];
        }

        const fileInputContainer = document.createElement("div");
        fileInputContainer.style.cssText = `
        display: flex;
        flex-direction: column;
        gap: 10px;
        width: 100%;
        margin-top: 10px;
    `;

        const addResumeBtn = document.createElement("button");
        addResumeBtn.id = "add-image-resume-btn";
        addResumeBtn.textContent = "添加图片简历";
        addResumeBtn.style.cssText = `
        padding: 8px 16px;
        border-radius: 6px;
        border: 1px solid rgba(0, 123, 255, 0.7);
        background: rgba(0, 123, 255, 0.1);
        color: rgba(0, 123, 255, 0.9);
        cursor: pointer;
        font-size: 14px;
        transition: all 0.2s ease;
        align-self: flex-start;
        white-space: nowrap;
    `;

        const fileNameDisplay = document.createElement("div");
        fileNameDisplay.id = "image-resume-filename";
        fileNameDisplay.style.cssText = `
        flex: 1;
        padding: 8px;
        border-radius: 6px;
        border: 1px solid #d1d5db;
        background: #f8fafc;
        color: #334155;
        font-size: 14px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    `;
        const resumeCount = state.settings.imageResumes
            ? state.settings.imageResumes.length
            : 0;
        fileNameDisplay.textContent =
            resumeCount > 0 ? `已上传 ${resumeCount} 个简历` : "未选择文件";

        const autoSendImageResumeToggle = (() => {
            const hasImageResumes =
                state.settings.imageResumes && state.settings.imageResumes.length > 0;
            const isValidState = hasImageResumes && settings.useAutoSendImageResume;
            if (!hasImageResumes) settings.useAutoSendImageResume = false;

            return createToggleSwitch(
                "auto-send-image-resume",
                isValidState,
                (checked) => {
                    if (
                        checked &&
                        (!state.settings.imageResumes ||
                            state.settings.imageResumes.length === 0)
                    ) {
                        showNotification("请先选择图片文件", "error");

                        const slider = document.querySelector(
                            "#toggle-auto-send-image-resume .toggle-slider"
                        );
                        const container = document.querySelector(
                            "#toggle-auto-send-image-resume .toggle-switch"
                        );

                        container.style.backgroundColor = "#e5e7eb";
                        slider.style.transform = "translateX(0)";
                        document.querySelector(
                            "#toggle-auto-send-image-resume input"
                        ).checked = false;
                    }
                    settings.useAutoSendImageResume = checked;
                    return true;
                },
                true
            );
        })();

        const hiddenFileInput = document.createElement("input");
        hiddenFileInput.id = "image-resume-input";
        hiddenFileInput.type = "file";
        hiddenFileInput.accept = ".jpg,.jpeg";
        hiddenFileInput.style.display = "none";

        const uploadedResumesContainer = document.createElement("div");
        uploadedResumesContainer.id = "uploaded-resumes-container";
        uploadedResumesContainer.style.cssText = `
        display: flex;
        flex-direction: column;
        gap: 8px;
        width: 100%;
    `;

        function renderResumeItem(index, resume) {
            const resumeItem = document.createElement("div");
            resumeItem.style.cssText = `
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 12px;
            border-radius: 6px;
            background: rgba(0, 0, 0, 0.05);
            font-size: 14px;
        `;

            const fileNameSpan = document.createElement("span");
            fileNameSpan.textContent = resume.path;
            fileNameSpan.style.cssText = `
            flex: 1;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-right: 8px;
        `;

            const deleteBtn = document.createElement("button");
            deleteBtn.textContent = "删除";
            deleteBtn.style.cssText = `
            padding: 4px 12px;
            border-radius: 4px;
            border: 1px solid rgba(255, 70, 70, 0.7);
            background: rgba(255, 70, 70, 0.1);
            color: rgba(255, 70, 70, 0.9);
            cursor: pointer;
            font-size: 12px;
        `;

            deleteBtn.addEventListener("click", () => {
                state.settings.imageResumes.splice(index, 1);

                resumeItem.remove();

                if (state.settings.imageResumes.length === 0) {
                    state.settings.useAutoSendImageResume = false;
                    const toggleInput = document.querySelector(
                        "#toggle-auto-send-image-resume input"
                    );
                    if (toggleInput) {
                        toggleInput.checked = false;
                        toggleInput.dispatchEvent(new Event("change"));
                    }
                }

                if (
                    typeof StatePersistence !== "undefined" &&
                    StatePersistence.saveState
                ) {
                    StatePersistence.saveState();
                }
            });

            resumeItem.appendChild(fileNameSpan);
            resumeItem.appendChild(deleteBtn);

            return resumeItem;
        }

        if (state.settings.imageResumes && state.settings.imageResumes.length > 0) {
            state.settings.imageResumes.forEach((resume, index) => {
                const resumeItem = renderResumeItem(index, resume);
                uploadedResumesContainer.appendChild(resumeItem);
            });
        }

        addResumeBtn.addEventListener("click", () => {
            if (state.settings.imageResumes.length >= 5) {
                if (typeof showNotification !== "undefined") {
                    showNotification("免费版最多添加5个图片简历", "info");
                } else {
                    alert("免费版最多添加5个图片简历");
                }
            } else {
                hiddenFileInput.click();
            }
        });

        hiddenFileInput.addEventListener("change", (e) => {
            if (e.target.files && e.target.files[0]) {
                const file = e.target.files[0];

                const fileName = file.name.toLowerCase();
                if (!fileName.endsWith('.jpg') && !fileName.endsWith('.jpeg')) {
                    if (typeof showNotification !== "undefined") {
                        showNotification("仅支持JPG格式的图片文件", "error");
                    } else {
                        alert("仅支持JPG格式的图片文件");
                    }
                    hiddenFileInput.value = "";
                    return;
                }

                const isDuplicate = state.settings.imageResumes.some(
                    (resume) => resume.path === file.name
                );
                if (isDuplicate) {
                    if (typeof showNotification !== "undefined") {
                        showNotification("该文件名已存在", "error");
                    } else {
                        alert("该文件名已存在");
                    }
                    return;
                }

                const reader = new FileReader();
                reader.onload = function (event) {
                    const newResume = {
                        path: file.name,
                        data: event.target.result,
                    };

                    state.settings.imageResumes.push(newResume);

                    const index = state.settings.imageResumes.length - 1;
                    const resumeItem = renderResumeItem(index, newResume);
                    uploadedResumesContainer.appendChild(resumeItem);

                    if (!state.settings.useAutoSendImageResume) {
                        state.settings.useAutoSendImageResume = true;
                        const toggleInput = document.querySelector(
                            "#toggle-auto-send-image-resume input"
                        );
                        if (toggleInput) {
                            toggleInput.checked = true;
                            toggleInput.dispatchEvent(new Event("change"));
                        }
                    }

                    if (
                        typeof StatePersistence !== "undefined" &&
                        StatePersistence.saveState
                    ) {
                        StatePersistence.saveState();
                    }
                };
                reader.readAsDataURL(file);
            }
        });

        fileInputContainer.append(
            addResumeBtn,
            uploadedResumesContainer,
            hiddenFileInput
        );
        imageResumeDescriptionContainer.append(autoSendImageResumeToggle);
        imageResumeSetting.append(fileInputContainer);

        const recruiterStatusSettingResult = createSettingItem(
            "投递招聘者状态（多选）",
            "筛选活跃状态符合要求的招聘者进行投递",
            () => document.querySelector("#recruiter-status-select .select-header")
        );

        const recruiterStatusSetting = recruiterStatusSettingResult.settingItem;

        const statusSelect = document.createElement("div");
        statusSelect.id = "recruiter-status-select";
        statusSelect.className = "custom-select";
        statusSelect.style.cssText = `
        position: relative;
        width: 100%;
        margin-top: 10px;
    `;

        const statusHeader = document.createElement("div");
        statusHeader.className = "select-header";
        statusHeader.style.cssText = `
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 12px 16px;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
        background: white;
        cursor: pointer;
        transition: all 0.2s ease;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        min-height: 44px;
    `;

        const statusDisplay = document.createElement("div");
        statusDisplay.className = "select-value";
        statusDisplay.style.cssText = `
        flex: 1;
        text-align: left;
        color: #334155;
        font-size: 14px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    `;
        statusDisplay.textContent = getStatusDisplayText();

        const statusIcon = document.createElement("div");
        statusIcon.className = "select-icon";
        statusIcon.innerHTML = "&#9660;";
        statusIcon.style.cssText = `
        margin-left: 10px;
        color: #64748b;
        transition: transform 0.2s ease;
    `;

        const statusClear = document.createElement("button");
        statusClear.className = "select-clear";
        statusClear.innerHTML = "×";
        statusClear.style.cssText = `
        background: none;
        border: none;
        color: #94a3b8;
        cursor: pointer;
        font-size: 16px;
        margin-left: 8px;
        display: none;
        transition: color 0.2s ease;
    `;

        statusHeader.append(statusDisplay, statusClear, statusIcon);

        const statusOptions = document.createElement("div");
        statusOptions.className = "select-options";
        statusOptions.style.cssText = `
        position: absolute;
        top: calc(100% + 6px);
        left: 0;
        right: 0;
        max-height: 240px;
        overflow-y: auto;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
        background: white;
        z-index: 100;
        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
        display: none;
        transition: all 0.2s ease;
        scrollbar-width: thin;
        scrollbar-color: #cbd5e1 #f1f5f9;
    `;

        statusOptions.innerHTML += `
        <style>
            .select-options::-webkit-scrollbar {
                width: 6px;
            }
            .select-options::-webkit-scrollbar-track {
                background: #f1f5f9;
                border-radius: 10px;
            }
            .select-options::-webkit-scrollbar-thumb {
                background: #cbd5e1;
                border-radius: 10px;
            }
            .select-options::-webkit-scrollbar-thumb:hover {
                background: #94a3b8;
            }
        </style>
    `;

        const statusOptionsList = [
            { value: "不限", text: "不限" },
            { value: "在线", text: "在线" },
            { value: "刚刚活跃", text: "刚刚活跃" },
            { value: "今日活跃", text: "今日活跃" },
            { value: "3日内活跃", text: "3日内活跃" },
            { value: "本周活跃", text: "本周活跃" },
            { value: "本月活跃", text: "本月活跃" },
            { value: "半年前活跃", text: "半年前活跃" },
        ];

        statusOptionsList.forEach((option) => {
            const statusOption = document.createElement("div");
            statusOption.className =
                "select-option" +
                (settings.recruiterActivityStatus &&
                Array.isArray(settings.recruiterActivityStatus) &&
                settings.recruiterActivityStatus.includes(option.value)
                    ? " selected"
                    : "");
            statusOption.dataset.value = option.value;
            statusOption.style.cssText = `
            padding: 12px 16px;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            font-size: 14px;
            color: #334155;
        `;

            const checkIcon = document.createElement("span");
            checkIcon.className = "check-icon";
            checkIcon.innerHTML = "✓";
            checkIcon.style.cssText = `
            margin-right: 8px;
            color: rgba(0, 123, 255, 0.9);
            font-weight: bold;
            display: ${settings.recruiterActivityStatus &&
            Array.isArray(settings.recruiterActivityStatus) &&
            settings.recruiterActivityStatus.includes(option.value)
                ? "inline"
                : "none"
            };
        `;

            const textSpan = document.createElement("span");
            textSpan.textContent = option.text;

            statusOption.append(checkIcon, textSpan);

            statusOption.addEventListener("click", (e) => {
                e.stopPropagation();
                toggleStatusOption(option.value);
            });

            statusOptions.appendChild(statusOption);
        });

        statusHeader.addEventListener("click", () => {
            statusOptions.style.display =
                statusOptions.style.display === "block" ? "none" : "block";
            statusIcon.style.transform =
                statusOptions.style.display === "block"
                    ? "rotate(180deg)"
                    : "rotate(0)";
        });

        statusClear.addEventListener("click", (e) => {
            e.stopPropagation();
            settings.recruiterActivityStatus = [];
            var aui=document.getElementById("ai-api-url-input");if(aui)aui.value=settings.ai.apiUrl||"";var aki=document.getElementById("ai-api-key-input");if(aki)aki.value=settings.ai.apiKey||"";var ami=document.getElementById("ai-model-input");if(ami)ami.value=settings.ai.model||"deepseek-chat";
            var rui=document.getElementById("user-resume-input");if(rui)rui.value=settings.resume||"";var tui=document.getElementById("greeting-template-input");if(tui)tui.value=settings.greetingTemplate||"";
            updateStatusOptions();
        });

        document.addEventListener("click", (e) => {
            if (!statusSelect.contains(e.target)) {
                statusOptions.style.display = "none";
                statusIcon.style.transform = "rotate(0)";
            }
        });

        statusHeader.addEventListener("mouseenter", () => {
            statusHeader.style.borderColor = "rgba(0, 123, 255, 0.5)";
            statusHeader.style.boxShadow = "0 0 0 3px rgba(0, 123, 255, 0.1)";
        });

        statusHeader.addEventListener("mouseleave", () => {
            if (!statusHeader.contains(document.activeElement)) {
                statusHeader.style.borderColor = "#e2e8f0";
                statusHeader.style.boxShadow = "0 1px 2px rgba(0, 0, 0, 0.05)";
            }
        });

        statusHeader.addEventListener("focus", () => {
            statusHeader.style.borderColor = "rgba(0, 123, 255, 0.7)";
            statusHeader.style.boxShadow = "0 0 0 3px rgba(0, 123, 255, 0.2)";
        });

        statusHeader.addEventListener("blur", () => {
            statusHeader.style.borderColor = "#e2e8f0";
            statusHeader.style.boxShadow = "0 1px 2px rgba(0, 0, 0, 0.05)";
        });

        statusSelect.append(statusHeader, statusOptions);
        recruiterStatusSetting.append(statusSelect);

        advancedSettingsPanel.append(
            autoReplySetting,
            autoSendResumeSetting,
            excludeHeadhuntersSetting,
            imageResumeSetting,
            recruiterStatusSetting
        );

        aiTab.addEventListener("click", () => {
            setActiveTab(aiTab, aiSettingsPanel);
        });

        advancedTab.addEventListener("click", () => {
            setActiveTab(advancedTab, advancedSettingsPanel);
        });

        const dialogFooter = document.createElement("div");
        dialogFooter.style.cssText = `
        padding: 15px 20px;
        border-top: 1px solid #e5e7eb;
        display: flex;
        justify-content: flex-end;
        gap: 10px;
        background: rgba(0, 0, 0, 0.03);
    `;

        const cancelBtn = createTextButton("取消", "#e5e7eb", () => {
            dialog.style.display = "none";
        });

        const saveBtn = createTextButton(
            "保存设置",
            "rgba(0, 123, 255, 0.9)",
            () => {
                try {
                    const aiRoleInput = document.getElementById("ai-role-input");
                    settings.ai.role = aiRoleInput ? aiRoleInput.value : "";
                    var ru3=document.getElementById("user-resume-input");if(ru3)settings.resume=ru3.value;
                    var tu3=document.getElementById("greeting-template-input");if(tu3)settings.greetingTemplate=tu3.value;

                    var au3=document.getElementById("ai-api-url-input");if(au3)settings.ai.apiUrl=au3.value;
                    var ak3=document.getElementById("ai-api-key-input");if(ak3)settings.ai.apiKey=ak3.value;
                    var am3=document.getElementById("ai-model-input");if(am3)settings.ai.model=am3.value||"deepseek-chat";
                    var cb3=document.getElementById("communication-base-url-input");if(cb3)settings.communicationBaseUrl=cb3.value;
                    var ct3=document.getElementById("communication-token-input");if(ct3)settings.communicationToken=ct3.value;
                    var ce3=document.getElementById("communication-sync-enabled-input");if(ce3)settings.communicationSyncEnabled=!!ce3.checked;


                    saveSettings();

                    showNotification("设置已保存");
                    dialog.style.display = "none";
                } catch (error) {
                    showNotification("保存失败: " + error.message, "error");
                    console.error("保存设置失败:", error);
                }
            }
        );

        dialogFooter.append(cancelBtn, saveBtn);

        dialogContent.append(
            tabsContainer,
            aiSettingsPanel,
            advancedSettingsPanel
        );
        dialog.append(dialogHeader, dialogContent, dialogFooter);

        dialog.addEventListener("click", (e) => {
            if (e.target === dialog) {
                dialog.style.display = "none";
            }
        });

        return dialog;
    }

    function showSettingsDialog() {
        let dialog = document.getElementById("boss-settings-dialog");
        if (!dialog) {
            dialog = createSettingsDialog();
            document.body.appendChild(dialog);
        }

        dialog.style.display = "flex";

        setTimeout(() => {
            dialog.classList.add("active");
            setTimeout(loadSettingsIntoUI, 100);
        }, 10);
    }

    function toggleStatusOption(value) {
        if (value === "不限") {
            settings.recruiterActivityStatus =
                settings.recruiterActivityStatus.includes("不限") ? [] : ["不限"];
        } else {
            if (settings.recruiterActivityStatus.includes("不限")) {
                settings.recruiterActivityStatus = [value];
            } else {
                if (settings.recruiterActivityStatus.includes(value)) {
                    settings.recruiterActivityStatus =
                        settings.recruiterActivityStatus.filter((v) => v !== value);
                } else {
                    settings.recruiterActivityStatus.push(value);
                }

                if (settings.recruiterActivityStatus.length === 0) {
                    settings.recruiterActivityStatus = ["不限"];
                }
            }
        }

        if (state.settings) {
            state.settings.recruiterActivityStatus = settings.recruiterActivityStatus;
        }

        updateStatusOptions();
    }

    function updateStatusOptions() {
        const options = document.querySelectorAll(
            "#recruiter-status-select .select-option"
        );
        options.forEach((option) => {
            const isSelected = settings.recruiterActivityStatus.includes(
                option.dataset.value
            );
            option.className = "select-option" + (isSelected ? " selected" : "");
            option.querySelector(".check-icon").style.display = isSelected
                ? "inline"
                : "none";

            if (option.dataset.value === "不限") {
                if (isSelected) {
                    options.forEach((opt) => {
                        if (opt.dataset.value !== "不限") {
                            opt.className = "select-option";
                            opt.querySelector(".check-icon").style.display = "none";
                        }
                    });
                }
            } else if (settings.recruiterActivityStatus.includes("不限")) {
                option.querySelector(".check-icon").style.display = "none";
                option.className = "select-option";
            }
        });

        document.querySelector(
            "#recruiter-status-select .select-value"
        ).textContent = getStatusDisplayText();

        document.querySelector(
            "#recruiter-status-select .select-clear"
        ).style.display =
            settings.recruiterActivityStatus.length > 0 &&
            !settings.recruiterActivityStatus.includes("不限")
                ? "inline"
                : "none";

        if (state.settings) {
            state.settings.recruiterActivityStatus = settings.recruiterActivityStatus;
        }
    }

    function getStatusDisplayText() {
        if (settings.recruiterActivityStatus.includes("不限")) {
            return "不限";
        }

        if (settings.recruiterActivityStatus.length === 0) {
            return "请选择";
        }

        if (settings.recruiterActivityStatus.length <= 2) {
            return settings.recruiterActivityStatus.join("、");
        }

        return `${settings.recruiterActivityStatus[0]}、${settings.recruiterActivityStatus[1]}等${settings.recruiterActivityStatus.length}项`;
    }

    function createDialogHeader(title, dialogId = "boss-settings-dialog") {
        const header = document.createElement("div");
        header.style.cssText = `
        padding: 16px 20px;
        background: #4F46E5;
        color: white;
        font-size: 18px;
        font-weight: 600;
        display: flex;
        justify-content: space-between;
        align-items: center;
        position: relative;
        border-radius: 12px 12px 0 0;
    `;

        const titleElement = document.createElement("div");
        titleElement.textContent = title;
        titleElement.style.fontWeight = "600";

        const closeBtn = document.createElement("button");
        closeBtn.innerHTML = "✕";
        closeBtn.title = "关闭";
        closeBtn.style.cssText = `
        width: 28px;
        height: 28px;
        background: rgba(255, 255, 255, 0.2);
        color: white;
        border-radius: 50%;
        display: flex;
        justify-content: center;
        align-items: center;
        cursor: pointer;
        transition: all 0.2s ease;
        border: none;
        font-size: 16px;
        font-weight: bold;
    `;

        closeBtn.addEventListener("mouseenter", () => {
            closeBtn.style.backgroundColor = "rgba(255, 255, 255, 0.3)";
            closeBtn.style.transform = "scale(1.1)";
        });

        closeBtn.addEventListener("mouseleave", () => {
            closeBtn.style.backgroundColor = "rgba(255, 255, 255, 0.2)";
            closeBtn.style.transform = "scale(1)";
        });

        closeBtn.addEventListener("click", () => {
            const dialog = document.getElementById(dialogId);
            if (dialog) {
                dialog.style.display = "none";
            }
        });

        header.append(titleElement, closeBtn);
        return header;
    }

    function showActivationDialog() {}

    function createSettingItem(title, description, controlGetter) {
        const settingItem = document.createElement("div");
        settingItem.className = "setting-item";
        settingItem.style.cssText = `
        padding: 15px;
        border-radius: 10px;
        margin-bottom: 15px;
        background: white;
        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
        border: 1px solid rgba(0, 123, 255, 0.1);
        display: flex;
        flex-direction: column;
    `;

        const titleElement = document.createElement("h4");
        titleElement.textContent = title;
        titleElement.style.cssText = `
        margin: 0 0 5px;
        color: #333;
        font-size: 16px;
        font-weight: 500;
    `;

        const descElement = document.createElement("p");
        descElement.textContent = description;
        descElement.style.cssText = `
        margin: 0;
        color: #666;
        font-size: 13px;
        line-height: 1.4;
    `;

        const descriptionContainer = document.createElement("div");
        descriptionContainer.style.cssText = `
        display: flex;
        justify-content: space-between;
        align-items: center;
        width: 100%;
    `;

        const textContainer = document.createElement("div");
        textContainer.append(titleElement, descElement);

        descriptionContainer.append(textContainer);

        settingItem.append(descriptionContainer);

        settingItem.addEventListener("click", () => {
            const control = controlGetter();
            if (control && typeof control.focus === "function") {
                control.focus();
            }
        });

        return {
            settingItem,
            descriptionContainer,
        };
    }

    function createToggleSwitch(
        id,
        isChecked,
        onChange) {
        const container = document.createElement("div");
        container.className = "toggle-container";
        container.style.cssText =
            "display: flex; justify-content: space-between; align-items: center;";

        const switchContainer = document.createElement("div");
        switchContainer.className = "toggle-switch";

        switchContainer.style.cssText = `
        position: relative;
        width: 50px;
        height: 26px;
        border-radius: 13px;
        background-color: ${isChecked ? "rgba(0, 123, 255, 0.9)" : "#e5e7eb"
        };
        cursor: pointer;
        opacity: 1;
    `;

        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.id = `toggle-${id}`;
        checkbox.checked = isChecked;
        checkbox.style.display = "none";

        const slider = document.createElement("span");
        slider.className = "toggle-slider";
        slider.style.cssText = `
        position: absolute;
        top: 3px;
        left: ${isChecked ? "27px" : "3px"};
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background-color: white;
        box-shadow: 0 1px 3px rgba(0,0,0,0.2);
        transition: none;
    `;

        const forceUpdateUI = (checked) => {


            checkbox.checked = checked;
            switchContainer.style.backgroundColor = checked
                ? "rgba(0, 123, 255, 0.9)"
                : "#e5e7eb";
            slider.style.left = checked ? "27px" : "3px";
        };

        checkbox.addEventListener("change", () => {
            if (false) {
                forceUpdateUI(!checkbox.checked);
                return;
            }

            let allowChange = true;

            if (onChange) {
                allowChange = onChange(checkbox.checked) !== false;
            }

            if (!allowChange) {
                forceUpdateUI(!checkbox.checked);
                return;
            }

            forceUpdateUI(checkbox.checked);
        });

        switchContainer.addEventListener("click", () => {
            if (false) {
                showActivationDialog();
                return;
            }

            const newState = !checkbox.checked;

            if (onChange) {
                if (onChange(newState) !== false) {
                    forceUpdateUI(newState);
                }
            } else {
                forceUpdateUI(newState);
            }
        });

        switchContainer.append(checkbox, slider);
        container.append(switchContainer);

        return container;
    }

    function createTextButton(text, backgroundColor, onClick) {
        const button = document.createElement("button");
        button.textContent = text;
        button.style.cssText = `
        padding: 9px 18px;
        border-radius: 8px;
        border: none;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s ease;
        background: ${backgroundColor};
        color: white;
    `;

        button.addEventListener("click", onClick);

        return button;
    }

    function addFocusBlurEffects(element) {
        element.addEventListener("focus", () => {
            element.style.borderColor = "rgba(0, 123, 255, 0.7)";
            element.style.boxShadow = "0 0 0 3px rgba(0, 123, 255, 0.2)";
        });

        element.addEventListener("blur", () => {
            element.style.borderColor = "#d1d5db";
            element.style.boxShadow = "none";
        });
    }

    function setActiveTab(tab, panel) {
        const tabs = document.querySelectorAll(".settings-tab");
        const panels = [
            document.getElementById("ai-settings-panel"),
            document.getElementById("advanced-settings-panel"),
        ];

        tabs.forEach((t) => {
            t.classList.remove("active");
            t.style.backgroundColor = "rgba(0, 0, 0, 0.05)";
            t.style.color = "#333";
        });

        panels.forEach((p) => {
            p.style.display = "none";
        });

        tab.classList.add("active");
        tab.style.backgroundColor = "rgba(0, 123, 255, 0.9)";
        tab.style.color = "white";

        panel.style.display = "block";
    }

    function showNotification(message, type = "success") {
        const notification = document.createElement("div");
        const bgColor =
            type === "success" ? "rgba(40, 167, 69, 0.9)" : "rgba(220, 53, 69, 0.9)";

        notification.style.cssText = `
        position: fixed;
        top: 20px;
        left: 50%;
        transform: translateX(-50%);
        background: ${bgColor};
        color: white;
        padding: 10px 15px;
        border-radius: 8px;
        box-shadow: 0 4px 10px rgba(0,0,0,0.2);
        z-index: 9999999;
        opacity: 0;
        transition: opacity 0.3s ease;
    `;

        notification.textContent = message;
        document.body.appendChild(notification);

        setTimeout(() => (notification.style.opacity = "1"), 10);
        setTimeout(() => {
            notification.style.opacity = "0";
            setTimeout(() => document.body.removeChild(notification), 300);
        }, 2000);
    }

    const Core = {
        CONFIG,

        messageObserver: null,
        lastProcessedMessage: null,
        processingMessage: false,
        currentMonitoredHR: null,
        communicationTokenBridgePromise: null,

        domCache: {},

        getCachedElement(selector, forceRefresh = false) {
            if (forceRefresh || !this.domCache[selector]) {
                this.domCache[selector] = document.querySelector(selector);
            }
            return this.domCache[selector];
        },

        getCachedElements(selector, forceRefresh = false) {
            if (forceRefresh || !this.domCache[selector + "[]"]) {
                this.domCache[selector + "[]"] = document.querySelectorAll(selector);
            }
            return this.domCache[selector + "[]"];
        },

        clearDomCache() {
            this.domCache = {};
        },

        extractJobInfo(card) {
            try {
                const jobLink = card.querySelector('a[href*="/job_detail/"]');
                if (!jobLink) {
                    this.log('未找到职位详情链接');
                    return null;
                }

                const href = jobLink.getAttribute('href');
                this.log(`职位链接: ${href}`);

                const jobIdMatch = href.match(/job_detail\/([^.]+)\.html/);
                if (!jobIdMatch) {
                    this.log('无法提取jobId');
                    return null;
                }

                const jobId = jobIdMatch[1];
                const securityIdMatch = href.match(/securityId=([^&]+)/);
                const securityId = securityIdMatch ? securityIdMatch[1] : null;

                const lidMatch = href.match(/lid=([^&]+)/);
                const lid = lidMatch ? lidMatch[1] : '';

                this.log(`提取信息 - jobId: ${jobId}, securityId: ${securityId ? '已获取' : '未找到'}, lid: ${lid}`);

                return {
                    jobId,
                    securityId,
                    lid
                };
            } catch (error) {
                this.log(`提取职位信息失败: ${error.message}`);
                return null;
            }
        },

        async sendFriendRequest(jobId, securityId, lid) {
            try {
                if (!securityId) {
                    throw new Error('缺少securityId参数');
                }

                const timestamp = Date.now();
                const url = `/wapi/zpgeek/friend/add.json?securityId=${encodeURIComponent(securityId)}&jobId=${encodeURIComponent(jobId || '')}&lid=${encodeURIComponent(lid || '')}&_=${timestamp}`;

                const response = await fetch(url, {
                    method: 'POST',
                    body: 'sessionId='
                });

                const result = await response.json();

                if (result.code === 0) {
                    return result;
                } else {
                    throw new Error(result.message || '请求失败');
                }
            } catch (error) {
                throw error;
            }
        },

        async startProcessing() {
            while (state.isRunning) {
                if (location.pathname.includes("/jobs")) await this.processJobList();
                else if (location.pathname.includes("/chat")) {
                    await this.syncRecentVisibleChatRepliesIfNeeded();
                    await this.handleChatPage();
                }

                if (!state.isRunning) {
                    break;
                }

                const waitTime = location.pathname.includes("/jobs")
                    ? getJobApplyInterval()
                    : CONFIG.BASIC_INTERVAL;
                await this.delay(waitTime);
            }
        },

        async autoScrollJobList() {
            return new Promise((resolve) => {
                const cardSelector = "li.job-card-box";
                const maxHistory = 3;
                const waitTime = CONFIG.BASIC_INTERVAL;
                let cardCountHistory = [];
                let isStopped = false;

                const scrollStep = async () => {
                    if (isStopped) return;

                    window.scrollTo({
                        top: document.documentElement.scrollHeight,
                        behavior: "smooth",
                    });
                    await this.delay(waitTime);

                    const cards = document.querySelectorAll(cardSelector);
                    const currentCount = cards.length;
                    cardCountHistory.push(currentCount);

                    if (cardCountHistory.length > maxHistory) cardCountHistory.shift();

                    if (
                        cardCountHistory.length === maxHistory &&
                        new Set(cardCountHistory).size === 1
                    ) {
                        this.log("当前页面岗位加载完成，开始沟通");
                        resolve(cards);
                        return;
                    }

                    scrollStep();
                };

                scrollStep();

                this.stopAutoScroll = () => {
                    isStopped = true;
                    resolve(null);
                };
            });
        },

        getJobCardUniqueKey(card) {
            const href = card?.querySelector('a[href*="/job_detail/"]')?.getAttribute("href") || "";
            const jobIdMatch = href.match(/job_detail\/([^.]+)\.html/);
            if (jobIdMatch?.[1]) {
                return jobIdMatch[1];
            }
            const title = this.getCardJobTitle(card);
            const companyName = this.getCardCompanyName(card);
            const hrName = this.getCardHrName(card);
            return [title, companyName, hrName].filter(Boolean).join("|") || this.normalizeCardText(card?.innerText || "");
        },

        async loadNextJobBatch() {
            const beforeCount = document.querySelectorAll("li.job-card-box").length;
            const beforeY = window.scrollY;
            const nextTop = beforeY + Math.max(window.innerHeight * 0.85, 600);
            window.scrollTo({
                top: nextTop,
                behavior: "smooth",
            });
            await this.delay(CONFIG.BASIC_INTERVAL * 2);
            const afterCount = document.querySelectorAll("li.job-card-box").length;
            const moved = Math.abs(window.scrollY - beforeY) > 80;
            if (afterCount > beforeCount || moved) {
                this.log(`尝试加载下一批岗位：卡片 ${beforeCount} -> ${afterCount}，滚动 ${Math.round(beforeY)} -> ${Math.round(window.scrollY)}`);
                return true;
            }
            this.log("没有加载到更多岗位");
            return false;
        },

        async prepareCurrentJobBatch() {
            const excludeHeadhunters = settings.excludeHeadhunters;
            const excludeOutsourcing = settings.excludeOutsourcing;
            const candidates = Array.from(
                document.querySelectorAll("li.job-card-box")
            ).filter((card) => {
                const cardKey = this.getJobCardUniqueKey(card);
                if (cardKey && state.processedJobIds.has(cardKey)) {
                    return false;
                }
                const title =
                    card.querySelector(".job-name")?.textContent?.toLowerCase() || "";
                const companyName = (
                    card.querySelector(".company-name")?.textContent ||
                    card.querySelector(".boss-name")?.textContent ||
                    card.querySelector(".company-text")?.textContent ||
                    ""
                )
                    .toLowerCase()
                    .trim();

                const addressText = (
                    card.querySelector(".job-address-desc")?.textContent ||
                    card.querySelector(".company-location")?.textContent ||
                    card.querySelector(".job-area")?.textContent ||
                    ""
                )
                    .toLowerCase()
                    .trim();
                const headhuntingElement = card.querySelector(".job-tag-icon");
                const altText = headhuntingElement ? headhuntingElement.alt : "";

                const includeMatch =
                    state.includeKeywords.length === 0 ||
                    state.includeKeywords.some((kw) => kw && title.includes(kw.trim()));

                const locationMatch =
                    state.locationKeywords.length === 0 ||
                    state.locationKeywords.some(
                        (kw) => kw && addressText.includes(kw.trim())
                    );

                const excludeCompanyMatch =
                    state.excludeCompanyKeywords.length === 0 ||
                    !companyName ||
                    !state.excludeCompanyKeywords.some(
                        (kw) => kw && companyName.includes(kw.trim())
                    );

                const excludeOutsourcingMatch =
                    !excludeOutsourcing ||
                    !companyName ||
                    !(settings.outsourcingKeywords || []).some(
                        (kw) => kw && companyName.includes(kw)
                    );

                const excludeHeadhunterMatch =
                    !excludeHeadhunters || !altText.includes("猎头");

                return (
                    includeMatch &&
                    locationMatch &&
                    excludeCompanyMatch &&
                    excludeOutsourcingMatch &&
                    excludeHeadhunterMatch
                );
            });

            const filteredCandidates = await this.filterOutRepliedJobs(candidates);
            state.jobList = filteredCandidates.slice(0, CONFIG.PERFORMANCE.BATCH_SIZE);
            state.currentIndex = 0;
            this.log(`当前批次可处理岗位：${state.jobList.length}/${filteredCandidates.length}，批次上限 ${CONFIG.PERFORMANCE.BATCH_SIZE}`);
        },

        async processJobList() {
            const activeStatusFilter = settings.recruiterActivityStatus;

            if (!state.jobList || state.jobList.length === 0) {
                await this.prepareCurrentJobBatch();

                if (!state.jobList.length) {
                    const loadedMore = await this.loadNextJobBatch();
                    if (loadedMore) {
                        await this.prepareCurrentJobBatch();
                    }
                    if (!state.jobList.length) {
                        this.log("没有更多符合条件的职位");
                        toggleProcess();
                    }
                    return;
                }
            }

            if (state.currentIndex >= state.jobList.length) {
                state.jobList = [];
                await this.delay(CONFIG.OPERATION_INTERVAL);
                const loadedMore = await this.loadNextJobBatch();
                if (!loadedMore) {
                    this.resetCycle();
                }
                return;
            }

            const currentCard = state.jobList[state.currentIndex];
            const currentCardKey = this.getJobCardUniqueKey(currentCard);
            if (currentCardKey) {
                state.processedJobIds.add(currentCardKey);
            }
            currentCard.scrollIntoView({ behavior: "smooth", block: "center" });
            currentCard.click();

            await this.delay(CONFIG.OPERATION_INTERVAL * 2);

            let activeTime = "未知";
            const onlineTag = document.querySelector(".boss-online-tag");
            if (onlineTag && onlineTag.textContent.trim() === "在线") {
                activeTime = "在线";
            } else {
                const activeTimeElement = document.querySelector(".boss-active-time");
                activeTime = activeTimeElement?.textContent?.trim() || "未知";
            }

            const isActiveStatusMatch =
                activeStatusFilter.includes("不限") ||
                activeStatusFilter.includes(activeTime);

            if (!isActiveStatusMatch) {
                this.log(`跳过: 招聘者状态 "${activeTime}"`);
                state.currentIndex++;
                return;
            }

            const includeLog = state.includeKeywords.length
                ? `职位名包含[${state.includeKeywords.join("、")}]`
                : "职位名不限";
            const locationLog = state.locationKeywords.length
                ? `工作地包含[${state.locationKeywords.join("、")}]`
                : "工作地不限";
            const excludeCompanyLog = state.excludeCompanyKeywords.length
                ? `排除公司[${state.excludeCompanyKeywords.join("、")}]`
                : "公司不限";
            this.log(
                `正在沟通：${++state.currentIndex}/${state.jobList.length
                }，${includeLog}，${locationLog}，${excludeCompanyLog}，招聘者"${activeTime}"`
            );

            const chatBtn = document.querySelector("a.op-btn-chat");
            if (chatBtn) {
                const btnText = chatBtn.textContent.trim();
                if (btnText === "立即沟通") {
                    let securityIdInfo = APIInterceptor.getCurrentSecurityId();

                    if (!securityIdInfo) {
                        this.log('未捕获到securityId，尝试从链接提取');
                        const jobInfo = this.extractJobInfo(currentCard);
                        if (jobInfo && jobInfo.securityId) {
                            securityIdInfo = {
                                securityId: jobInfo.securityId,
                                lid: jobInfo.lid,
                                jobId: jobInfo.jobId
                            };
                        }
                    }

                    if (securityIdInfo && securityIdInfo.securityId) {
                        try {
                            await this.sendFriendRequest(
                                securityIdInfo.jobId || '',
                                securityIdInfo.securityId,
                                securityIdInfo.lid || ''
                            );

                            await this.delay(CONFIG.OPERATION_INTERVAL);
                            const deliveredJobInfo = this.extractJobInfo(currentCard);
                            if (deliveredJobInfo?.jobId) {
                                state.pendingCommunicationJobs.set(
                                    `${(document.querySelector(".boss-info-attr .name")?.textContent || "").trim()}-${(document.querySelector(".company-name")?.textContent || "").trim()}`.toLowerCase(),
                                    deliveredJobInfo
                                );
                            }
                            await this.syncCommunicatedJob(currentCard);
                            this.markJobDelivered();
                        } catch (error) {
                            this.log(`发送请求失败: ${error.message}，回退到点击方式`);
                            chatBtn.click();
                            await this.handleGreetingModal();
                            const deliveredJobInfo = this.extractJobInfo(currentCard);
                            if (deliveredJobInfo?.jobId) {
                                state.pendingCommunicationJobs.set(
                                    `${(document.querySelector(".boss-info-attr .name")?.textContent || "").trim()}-${(document.querySelector(".company-name")?.textContent || "").trim()}`.toLowerCase(),
                                    deliveredJobInfo
                                );
                            }
                            await this.syncCommunicatedJob(currentCard);
                            this.markJobDelivered();
                        }
                    } else {
                        this.log(`无法获取securityId，使用点击方式`);
                        chatBtn.click();
                        await this.handleGreetingModal();
                        const deliveredJobInfo = this.extractJobInfo(currentCard);
                        if (deliveredJobInfo?.jobId) {
                            state.pendingCommunicationJobs.set(
                                `${(document.querySelector(".boss-info-attr .name")?.textContent || "").trim()}-${(document.querySelector(".company-name")?.textContent || "").trim()}`.toLowerCase(),
                                deliveredJobInfo
                            );
                        }
                        await this.syncCommunicatedJob(currentCard);
                        this.markJobDelivered();
                    }
                }
            }
        },

        async handleGreetingModal() {
            await this.delay(CONFIG.OPERATION_INTERVAL * 4);

            const btn = [
                ...document.querySelectorAll(".default-btn.cancel-btn"),
            ].find((b) => b.textContent.trim() === "留在此页");

            if (btn) {
                btn.click();
                await this.delay(CONFIG.OPERATION_INTERVAL * 2);
            }
        },

        async handleChatPage() {
            const latestChatLi = await this.waitForElement(this.getLatestChatLi);
            if (!latestChatLi) return;

            const nameEl = latestChatLi.querySelector(".name-text");
            const companyEl = latestChatLi.querySelector(
                ".name-box span:nth-child(2)"
            );
            const name = (nameEl?.textContent || "未知").trim();
            const company = (companyEl?.textContent || "").trim();
            const hrKey = `${name}-${company}`.toLowerCase();

            // 如果当前正在监控同一个 HR，且 observer 正常，则跳过繁重逻辑
            if (this.currentMonitoredHR === hrKey && this.messageObserver) {
                return;
            }

            this.currentMonitoredHR = hrKey;
            this.resetMessageState();

            if (this.messageObserver) {
                this.messageObserver.disconnect();
                this.messageObserver = null;
            }

            if (
                settings.communicationIncludeKeywords &&
                settings.communicationIncludeKeywords.trim()
            ) {
                await this.simulateClick(latestChatLi.querySelector(".figure"));
                await this.delay(CONFIG.OPERATION_INTERVAL * 2);

                const positionName = this.getPositionName();
                const includeKeywords = settings.communicationIncludeKeywords
                    .toLowerCase()
                    .split(/[，,]/)
                    .map((kw) => kw.trim())
                    .filter((kw) => kw.length > 0);

                const positionNameLower = positionName.toLowerCase();
                const isMatch = includeKeywords.some((keyword) =>
                    positionNameLower.includes(keyword)
                );

                if (!isMatch) {
                    this.log(`跳过岗位，不含关键词[${includeKeywords.join(", ")}]`);

                    if (settings.communicationMode === "auto") {
                        await this.scrollUserList();
                    }
                    return;
                }
            }

            if (!latestChatLi.classList.contains("last-clicked")) {
                await this.simulateClick(latestChatLi.querySelector(".figure"));
                latestChatLi.classList.add("last-clicked");

                await this.delay(CONFIG.OPERATION_INTERVAL);
                await HRInteractionManager.handleHRInteraction(hrKey);

                if (settings.communicationMode === "auto") {
                    await this.scrollUserList();
                }
            }

            await this.setupMessageObserver(hrKey);
        },

        async scrollUserList() {
            const userListContent = document.querySelector(".user-list-content");
            if (userListContent) {
                const totalHeight = userListContent.scrollHeight;
                const clientHeight = userListContent.clientHeight;
                const maxScrollTop = totalHeight - clientHeight;

                if (maxScrollTop <= 0) {
                    return;
                }

                const scrollSteps = Math.floor(Math.random() * 3) + 3;

                for (let i = 0; i < scrollSteps; i++) {
                    const randomTop = Math.floor(Math.random() * maxScrollTop);

                    userListContent.scrollTo({
                        top: randomTop,
                        behavior: "smooth",
                    });

                    const randomDelay = Math.floor(Math.random() * 2000) + 1000;
                    await this.delay(randomDelay);
                }

                const finalPosition = Math.random() > 0.5 ? maxScrollTop : 0;
                userListContent.scrollTo({
                    top: finalPosition,
                    behavior: "smooth",
                });
            }
        },

        resetMessageState() {
            this.lastProcessedMessage = null;
            this.processingMessage = false;
        },

        async setupMessageObserver(hrKey) {
            const chatContainer = await this.waitForElement(".chat-message .im-list");
            if (!chatContainer) return;

            this.messageObserver = new MutationObserver(async (mutations) => {
                let hasNewFriendMessage = false;
                for (const mutation of mutations) {
                    if (mutation.type === "childList" && mutation.addedNodes.length > 0) {
                        hasNewFriendMessage = Array.from(mutation.addedNodes).some((node) =>
                            node.classList?.contains("item-friend")
                        );
                        if (hasNewFriendMessage) break;
                    }
                }

                if (hasNewFriendMessage) {
                    await this.handleNewMessage(hrKey);
                }
            });

            this.messageObserver.observe(chatContainer, {
                childList: true,
                subtree: true,
            });
        },

        async handleNewMessage(hrKey) {
            if (!state.isRunning) return;
            if (this.processingMessage) return;

            this.processingMessage = true;

            try {
                await this.delay(CONFIG.OPERATION_INTERVAL);

                const lastMessage = await this.getLastFriendMessageText();
                if (!lastMessage) return;

                const cleanedMessage = this.cleanMessage(lastMessage);
                const shouldSendResumeOnly = cleanedMessage.includes("简历");
                const isRejectedReply = this.isNotSuitableReply(cleanedMessage);

                if (cleanedMessage === this.lastProcessedMessage) return;

                this.lastProcessedMessage = cleanedMessage;
                this.log(`已同意交换，对方: ${lastMessage}`);
                const repliedJobInfo = state.pendingCommunicationJobs.get(hrKey);
                if (repliedJobInfo?.jobId) {
                    await this.syncRepliedJob(repliedJobInfo);
                    if (isRejectedReply) {
                        await this.markJobStatus(repliedJobInfo.jobId, "NOT_SUITABLE");
                        this.log(`HR回复命中不合适关键词，已标记为不合适: ${cleanedMessage}`);
                    } else {
                        await this.markJobAsReplied(repliedJobInfo.jobId);
                    }
                }

                await this.delay(CONFIG.DELAYS.MEDIUM_SHORT);
                const updatedMessage = await this.getLastFriendMessageText();
                if (
                    updatedMessage &&
                    this.cleanMessage(updatedMessage) !== cleanedMessage
                ) {
                    await this.handleNewMessage(hrKey);
                    return;
                }

                if (isRejectedReply) {
                    this.log(`HR回复包含拒绝话术，跳过发送简历和图片简历: ${cleanedMessage}`);
                    return;
                }

                const autoSendResume = settings.useAutoSendResume;
                const autoReplyEnabled = settings.autoReply;

                if (shouldSendResumeOnly && autoSendResume) {
                    this.log('对方提到"简历"，正在发送简历');
                    await HRInteractionManager.sendResumeOnceForConversation(hrKey);
                } else if (autoReplyEnabled) {
                    await HRInteractionManager.handleHRInteraction(hrKey);
                }

                await this.delay(CONFIG.DELAYS.MEDIUM_SHORT);
            } catch (error) {
                this.log(`处理消息出错: ${error.message}`);
            } finally {
                this.processingMessage = false;
            }
        },

        cleanMessage(message) {
            if (!message) return "";

            let clean = message.replace(/<[^>]*>/g, "");
            clean = this.decodeBossPrivateUseDigits(clean);
            clean = clean
                .trim()
                .replace(/\s+/g, " ")
                .replace(/[\u200B-\u200D\uFEFF]/g, "");
            return clean;
        },

        decodeBossPrivateUseDigits(text) {
            return String(text || "").replace(/[\uE030-\uE039]/g, (char) =>
                String(char.codePointAt(0) - 0xE030)
            );
        },

        isNotSuitableReply(message) {
            const text = this.cleanMessage(message || "");
            if (!text) {
                return false;
            }
            return [
                "不合适",
                "不太合适",
                "不匹配",
                "不太匹配",
                "不符合",
                "不适合",
                "对不起",
                "抱歉",
                "暂不考虑",
                "暂时不考虑",
                "暂时不太合适",
                "祝你",
                "祝您",
                "祝求职顺利",
                "祝你求职顺利",
                "祝您求职顺利",
                "有机会再联系"
            ].some((keyword) => text.includes(keyword));
        },

        getLatestChatLi() {
            return document.querySelector(
                'ul[role="group"] li[role="listitem"][class]:has(.friend-content-warp)'
            );
        },

        getVisibleChatListItems() {
            return Array.from(
                document.querySelectorAll('ul[role="group"] li[role="listitem"][class]:has(.friend-content-warp)')
            ).filter((item) => {
                const rect = item.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0;
            });
        },

        extractJobDetail(){try{var t=this.getPositionName()||"",d="",r="";var ss=[".job-detail-box .job-sec-text",".job-sec-text"];for(var i=0;i<ss.length;i++){var el=document.querySelector(ss[i]);if(el&&el.textContent.trim()){d=el.textContent.trim();break;}}var tags=document.querySelectorAll(".job-tag-box .tag-item,.job-tags .tag-item");if(tags.length>0)r=Array.from(tags).map(function(x){return x.textContent.trim()}).join("、");return{title:t,description:[d,r?"技能:"+r:""].filter(Boolean).join(String.fromCharCode(10))||t};}catch(e){return{title:this.getPositionName()||"",description:""};}},
        async generateGreeting(jobInfo){if(!settings.ai.apiKey){this.log("请先配置API Key");return"";}if(!settings.resume||!settings.resume.trim()){this.log("请填写个人简历");return"";}var gt="";if(state.settings.greetingsList&&state.settings.greetingsList.length>0){gt="自我介绍:"+String.fromCharCode(10)+state.settings.greetingsList.map(function(g){return g.content;}).filter(Boolean).join(String.fromCharCode(10))+String.fromCharCode(10)+String.fromCharCode(10);}var tpl=settings.greetingTemplate||"你是求职者，根据以下信息写3-5条给HR的打招呼消息。风格参考：您好，我是应届生，有相关实习经历，熟练使用相关工具，曾达成具体成果。对贵公司岗位很感兴趣，方便的话发简历给您。每条20-100字，口语化，用换行分开，只输出内容。"+String.fromCharCode(10)+String.fromCharCode(10)+"我的简历：{resume}"+String.fromCharCode(10)+"目标岗位：{job_title}"+String.fromCharCode(10)+"岗位要求：{job_requirements}";var p=tpl.replace(/{resume}/g,settings.resume).replace(/{job_title}/g,jobInfo.title).replace(/{job_requirements}/g,jobInfo.description);try{this.log("AI生成招呼语...");var txt=await this.requestAi(p,"你是求职者在BOSS直聘上给HR发第一条消息。参考示例风格：直接自我介绍，说明学历、经验、技能、成果，表达岗位意向，询问能否发简历。用语礼貌自然，像真人聊天，每条消息20-100字。发送3-5条消息，用换行分开。只输出打招呼内容，不要任何解释。",{timeout:CONFIG.API.GREETING_TIMEOUT,retryCount:2,retryDelay:1500,context:"AI招呼语生成"});if(txt){state.currentGeneratedGreeting=txt;var pv=document.getElementById("greeting-preview-text");if(pv)pv.value=txt;this.log("生成成功");return txt;}}catch(e){this.log("失败:"+e.message);}return"";},
        updateGreetingPreview(text){var pv=document.getElementById("greeting-preview-text");if(pv)pv.value=text;},
        getPositionName() {
            try {
                const positionNameElement =
                    Core.getCachedElement(".position-name", true) ||
                    Core.getCachedElement(".job-name", true) ||
                    Core.getCachedElement(
                        '[class*="position-content"] .left-content .position-name',
                        true
                    ) ||
                    document.querySelector(".position-name") ||
                    document.querySelector(".job-name");

                if (positionNameElement) {
                    return positionNameElement.textContent.trim();
                } else {
                    // Silent failure is better here as we might check multiple times
                    return "";
                }
            } catch (e) {
                Core.log(`获取岗位名称出错: ${e.message}`);
                return "";
            }
        },

        async aiReply() {
            if (!state.isRunning) return;
            try {
                const autoReplyEnabled = JSON.parse(
                    localStorage.getItem("autoReply") || "false"
                );
                if (!autoReplyEnabled) {
                    return false;
                }

                const lastMessage = await this.getLastFriendMessageText();
                if (!lastMessage) return false;

                const today = new Date().toISOString().split("T")[0];
                if (state.ai.lastAiDate !== today) {
                    state.ai.replyCount = 0;
                    state.ai.lastAiDate = today;
                    StatePersistence.saveState();
                }

                const maxReplies = 10;
                if (state.ai.replyCount >= maxReplies) {
                    this.log(`AI回复已达上限`);
                    return false;
                }

                const aiReplyText = await this.requestAi(lastMessage);
                if (!aiReplyText) return false;

                this.log(`AI回复: ${aiReplyText.slice(0, 30)}...`);
                state.ai.replyCount++;
                StatePersistence.saveState();

                const inputBox = await this.waitForElement("#chat-input");
                if (!inputBox) return false;

                inputBox.textContent = "";
                inputBox.focus();
                document.execCommand("insertText", false, aiReplyText);
                await this.delay(CONFIG.OPERATION_INTERVAL / 10);

                const sendButton = DOMCache.get(".btn-send");
                if (sendButton) {
                    await this.simulateClick(sendButton);
                } else {
                    const enterKeyEvent = new KeyboardEvent("keydown", {
                        key: "Enter",
                        keyCode: 13,
                        code: "Enter",
                        which: 13,
                        bubbles: true,
                    });
                    inputBox.dispatchEvent(enterKeyEvent);
                }

                return true;
            } catch (error) {
                ErrorHandler.handle(error, 'Core.aiReply');
                this.log(`AI回复出错: ${error.message}`);
                return false;
            }
        },

        extractAiText(result) {
            const directContent = result?.choices?.[0]?.message?.content;
            if (typeof directContent === "string" && directContent.trim()) {
                return directContent.trim();
            }

            if (Array.isArray(directContent)) {
                const mergedText = directContent
                    .map((item) => item?.text || item?.content || "")
                    .join("")
                    .trim();
                if (mergedText) {
                    return mergedText;
                }
            }

            const choiceText = result?.choices?.[0]?.text;
            if (typeof choiceText === "string" && choiceText.trim()) {
                return choiceText.trim();
            }

            const outputText = result?.output_text;
            if (typeof outputText === "string" && outputText.trim()) {
                return outputText.trim();
            }

            return "";
        },

        async requestAi(message, systemRole, options = {}) {
            const authToken = (settings.ai.apiKey || "").trim();
            const apiUrl = normalizeAiApiUrl(settings.ai.apiUrl);
            const modelName = (settings.ai.model || "").trim();
            const timeout = Number.isFinite(options.timeout) ? options.timeout : CONFIG.API.TIMEOUT;
            const retryCount = Number.isFinite(options.retryCount) ? options.retryCount : CONFIG.API.RETRY_COUNT;
            const retryDelay = Number.isFinite(options.retryDelay) ? options.retryDelay : CONFIG.API.RETRY_DELAY;
            const maxTokens = Number.isFinite(options.maxTokens) ? options.maxTokens : 512;
            const temperature = typeof options.temperature === "number" ? options.temperature : 0.9;
            const topP = typeof options.topP === "number" ? options.topP : 0.8;
            const context = options.context || "AI请求";

            if (!authToken) {
                throw new Error("未配置 API Key");
            }

            if (!apiUrl) {
                throw new Error("未配置 API 地址");
            }

            if (!modelName) {
                throw new Error("未配置 model_name");
            }

            const requestBody = {
                model: modelName,
                messages: [
                    {
                        role: "system",
                        content:
                            systemRole ||
                            localStorage.getItem("aiRole") ||
                            "你是求职者，用口语化表达，言简意赅。",
                    },
                    { role: "user", content: message },
                ],
                temperature,
                top_p: topP,
                max_tokens: maxTokens,
            };

            const requestOnce = () => new Promise((resolve, reject) => {
                GM_xmlhttpRequest({
                    method: "POST",
                    url: apiUrl,
                    timeout,
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: "Bearer " + authToken,
                    },
                    data: JSON.stringify(requestBody),
                    onload: (response) => {
                        try {
                            if (response.status < 200 || response.status >= 300) {
                                throw new Error(
                                    `HTTP ${response.status}: ${response.responseText.substring(0, 200)}`
                                );
                            }

                            const result = JSON.parse(response.responseText);
                            if (result?.error?.message) {
                                throw new Error(result.error.message);
                            }
                            if (result.code !== undefined && result.code !== 0) {
                                throw new Error(
                                    `API错误: ${result.message || "未知错误"}（Code: ${result.code}）`
                                );
                            }

                            const text = this.extractAiText(result);
                            if (!text) {
                                throw new Error(
                                    "未知API格式: " + response.responseText.substring(0, 200)
                                );
                            }

                            resolve(text);
                        } catch (error) {
                            reject(new Error("解析失败: " + error.message));
                        }
                    },
                    ontimeout: () => reject(new Error("请求超时，请检查接口响应速度")),
                    onerror: (error) =>
                        reject(new Error("网络请求失败: " + JSON.stringify(error))),
                });
            });

            let attempt = 0;
            let lastError = null;
            const maxAttempts = Math.max(1, retryCount);

            while (attempt < maxAttempts) {
                try {
                    if (attempt > 0) {
                        this.log(`${context}重试中：第${attempt + 1}/${maxAttempts}次`);
                    }
                    return await requestOnce();
                } catch (error) {
                    lastError = error;
                    attempt += 1;
                    const isRetryable = /请求超时|网络请求失败|HTTP 5\d{2}/.test(error.message);
                    if (attempt >= maxAttempts || !isRetryable) {
                        throw lastError;
                    }
                    await this.delay(retryDelay);
                }
            }

            throw lastError || new Error(`${context}失败`);
        },

        async getLastFriendMessageText() {
            try {
                const chatContainer = DOMCache.get(".chat-message .im-list");
                if (!chatContainer) return null;

                const friendMessages = Array.from(
                    chatContainer.querySelectorAll("li.message-item.item-friend")
                );
                if (friendMessages.length === 0) return null;

                const lastMessageEl = friendMessages[friendMessages.length - 1];
                const textEl = lastMessageEl.querySelector(".text span");
                return textEl?.textContent?.trim() || null;
            } catch (error) {
                ErrorHandler.handle(error, 'Core.getLastFriendMessageText');
                this.log(`获取消息出错: ${error.message}`);
                return null;
            }
        },

        getTextBySelectors(selectors, root = document) {
            for (const selector of selectors || []) {
                const element = root.querySelector(selector);
                const text = this.cleanMessage(element?.textContent || "");
                if (text) {
                    return text;
                }
            }
            return "";
        },

        isMeaningfulText(text) {
            const normalized = this.cleanMessage(text || "");
            if (!normalized) {
                return false;
            }
            return !/^[-—–_./\\|]+$/.test(normalized) &&
                !/^(未知|待识别|匿名企业|薪资待补充|暂无|无|不限|undefined|null)$/i.test(normalized);
        },

        getCleanTextBySelectors(root, selectors) {
            for (const selector of selectors || []) {
                const text = this.getTextBySelectors([selector], root);
                if (this.isMeaningfulText(text)) {
                    return text;
                }
            }
            return "";
        },

        getFirstHrefBySelectors(root, selectors) {
            for (const selector of selectors || []) {
                const element = root.querySelector(selector);
                const href = element?.href || element?.getAttribute?.("href") || "";
                if (href) {
                    return href;
                }
            }
            return "";
        },

        normalizeCardText(text) {
            return this.cleanMessage(text || "")
                .replace(/立即沟通|继续沟通|感兴趣|不感兴趣|查看详情/g, " ")
                .replace(/\s+/g, " ")
                .trim();
        },

        getCardFieldFromText(card, type) {
            const rawText = card?.innerText || card?.textContent || "";
            const text = this.normalizeCardText(rawText);
            if (!text) {
                return "";
            }
            const lines = String(rawText || "").split(/\n|\r| {2,}/)
                .map((item) => this.cleanMessage(item))
                .filter(Boolean);
            if (type === "salary") {
                return lines.find((line) => /\d+(?:\.\d+)?\s*[kK](?:\s*[-~至]\s*\d+(?:\.\d+)?\s*[kK]?)?(?:[·.。]?\s*\d+\s*薪)?/.test(line)) ||
                    (text.match(/\d+(?:\.\d+)?\s*[kK](?:\s*[-~至]\s*\d+(?:\.\d+)?\s*[kK]?)?(?:[·.。]?\s*\d+\s*薪)?/) || [""])[0];
            }
            if (type === "location") {
                return lines.find((line) => /北京|上海|广州|深圳|杭州|武汉|成都|南京|苏州|西安|长沙|重庆|天津|郑州|合肥|厦门|青岛|无锡|东莞|佛山|远程/.test(line)) || "";
            }
            return "";
        },

        getCardJobTitle(card) {
            return this.getCleanTextBySelectors(card, [
                ".job-name",
                ".job-title",
                ".job-card-left .job-name",
                ".job-primary .job-name",
                ".job-info .job-name",
                ".position-name",
                '[class*="job-name"]',
                '[class*="job-title"]',
                'a[href*="/job_detail/"]'
            ]);
        },

        getCardCompanyName(card) {
            const text = this.getCleanTextBySelectors(card, [
                ".company-name",
                ".company-text",
                ".company-info .name",
                ".company-info .company-name",
                ".company-card .name",
                ".company-card .company-name",
                ".boss-name",
                '[class*="company-name"]',
                '[class*="companyName"]',
                '[class*="company-text"]'
            ]);
            if (text) {
                return text.replace(/已上市|未融资|天使轮|A轮|B轮|C轮|D轮及以上|不需要融资/g, "").trim();
            }
            return "";
        },

        getCardSalaryRange(card) {
            return this.getCleanTextBySelectors(card, [
                ".salary",
                ".job-salary",
                ".job-card-left .salary",
                ".job-card-body .salary",
                ".info-primary .salary",
                ".job-limit .red",
                ".job-limit span",
                ".job-info .red",
                ".job-primary .red",
                ".job-card-left .red",
                '[class*="salary"]',
                ".red"
            ]) || this.getCardFieldFromText(card, "salary");
        },

        getCardSalaryDiagnostics(card) {
            const selectors = [
                ".salary",
                ".job-salary",
                ".job-card-left .salary",
                ".job-card-body .salary",
                ".info-primary .salary",
                ".job-limit .red",
                ".job-limit span",
                ".job-info .red",
                ".job-primary .red",
                ".job-card-left .red",
                '[class*="salary"]',
                ".red"
            ];
            const selectorHits = selectors.map((selector) => ({
                selector,
                text: this.getTextBySelectors([selector], card),
            })).filter((item) => item.text);
            const rawText = this.normalizeCardText(card?.innerText || card?.textContent || "");
            const regexMatches = rawText.match(/\d+(?:\.\d+)?\s*[kK](?:\s*[-~至]\s*\d+(?:\.\d+)?\s*[kK]?)?(?:[·.。]?\s*\d+\s*薪)?/g) || [];
            return {
                selectorHits: selectorHits.slice(0, 8),
                regexMatches: regexMatches.slice(0, 5),
                cardText: rawText.slice(0, 300),
            };
        },

        getCardLocation(card) {
            return this.getCleanTextBySelectors(card, [
                ".job-address-desc",
                ".job-area",
                ".job-location",
                ".company-location",
                ".job-limit .gray",
                ".job-info .gray",
                '[class*="address"]',
                '[class*="location"]',
                '[class*="area"]'
            ]) || this.getCardFieldFromText(card, "location");
        },

        getCardHrName(card) {
            const text = this.getCleanTextBySelectors(card, [
                ".recruiter-info .name",
                ".recruiter-info .boss-name",
                ".boss-info .name",
                ".boss-info-attr .name",
                ".start-chat-boss .name",
                ".detail-boss-info .name",
                '[class*="recruiter"] [class*="name"]'
            ]);
            return this.cleanHrName(text);
        },

        cleanHrName(text, companyName = "") {
            const cleaned = this.cleanMessage(text || "")
                .replace(/在线|刚刚活跃|今日活跃|本周活跃|月内活跃/g, "")
                .replace(/招聘者|招聘官|HR|人事|负责人/g, "")
                .replace(/[：:|｜].*$/g, "")
                .trim();
            if (!this.isMeaningfulText(cleaned)) {
                return "";
            }
            if (companyName && cleaned === this.cleanMessage(companyName)) {
                return "";
            }
            return cleaned;
        },

        getAttributeBySelectors(selectors, attribute, root = document) {
            for (const selector of selectors || []) {
                const element = root.querySelector(selector);
                const value = element?.getAttribute?.(attribute)?.trim();
                if (value) {
                    return value;
                }
            }
            return "";
        },

        normalizeSalaryRange(text) {
            const normalized = this.cleanMessage(text || "");
            if (!this.isMeaningfulText(normalized)) {
                return "";
            }
            if (/^[-—–_./\\|]+$/.test(normalized) || /^[-—–_./\\|]*K(?:[·.。]薪)?$/i.test(normalized)) {
                return "";
            }
            return normalized;
        },

        getConversationTimeline() {
            try {
                const chatContainer = DOMCache.get(".chat-message .im-list");
                if (!chatContainer) {
                    return [];
                }
                const items = Array.from(
                    chatContainer.querySelectorAll("li.message-item")
                );
                return items.slice(-12).map((item, index) => {
                    const role = item.classList.contains("item-friend") ? "HR" : "CANDIDATE";
                    const content = this.cleanMessage(
                        item.querySelector(".text")?.textContent ||
                        item.querySelector(".message-content")?.textContent ||
                        ""
                    );
                    const timeText = this.cleanMessage(
                        item.querySelector(".time")?.textContent ||
                        item.querySelector(".message-time")?.textContent ||
                        item.getAttribute("data-time") ||
                        ""
                    );
                    return {
                        seq: index + 1,
                        role,
                        content,
                        time: timeText,
                    };
                }).filter((item) => item.content);
            } catch (error) {
                this.log(`提取沟通时间线失败: ${error.message}`);
                return [];
            }
        },

        getCurrentChatContext() {
            const hrName = this.cleanHrName(this.getTextBySelectors([
                ".boss-info-attr .name",
                ".boss-info .name",
                ".job-boss-info .name",
                ".recruiter-info .name",
                ".detail-boss-info .name",
                ".name-box .name-text",
                ".chat-info-head .name",
            ]));
            const hrTitle = this.getTextBySelectors([
                ".boss-info-attr .gray",
                ".boss-info-attr .desc",
                ".geek-info .job",
            ]);
            const companyName = this.getTextBySelectors([
                ".company-name",
                ".company-text",
                ".name-box span:nth-child(2)",
            ]);
            const companyIndustry = this.getTextBySelectors([
                ".company-tab .company-info-tag span",
                ".company-info-tag span",
                ".company-info-box .label-item:last-child",
            ]);
            const companySize = this.getTextBySelectors([
                ".company-info-box .label-item",
                ".company-scale",
                ".company-tab .label-item",
            ]);
            const companyLogo = this.getAttributeBySelectors([
                ".company-logo img",
                ".company-card img",
                ".company-info-box img",
            ], "src");
            const jobLocation = this.getTextBySelectors([
                ".job-address-desc",
                ".location-address",
                ".job-area",
            ]);
            const salaryRange = this.getTextBySelectors([
                ".salary",
                ".job-salary",
                ".red",
            ]);
            return {
                hrName,
                hrTitle,
                companyName,
                companyIndustry,
                companySize,
                companyLogo,
                jobLocation,
                salaryRange,
            };
        },

        normalizeCommunicationKeyPart(value) {
            return this.cleanMessage(value || "")
                .toLowerCase()
                .replace(/\s+/g, "");
        },

        buildCommunicationKey(parts = {}) {
            const platform = this.normalizeCommunicationKeyPart(parts.platform || "BOSS");
            const jobId = this.normalizeCommunicationKeyPart(parts.jobId || "");
            const companyName = this.normalizeCommunicationKeyPart(parts.companyName || "");
            const hrName = this.normalizeCommunicationKeyPart(parts.hrName || parts.hrKey || "");
            return [platform, jobId || "unknown-job", companyName || "unknown-company", hrName || "unknown-hr"].join(":");
        },

        parseBossRelativeTime(text) {
            const value = this.cleanMessage(text || "");
            if (!value) {
                return null;
            }
            const now = new Date();
            if (/刚刚|刚才/.test(value)) {
                return now;
            }
            const minuteMatch = value.match(/(\d+)\s*分钟前/);
            if (minuteMatch) {
                return new Date(now.getTime() - Number(minuteMatch[1]) * 60 * 1000);
            }
            const hourMatch = value.match(/(\d+)\s*小时前/);
            if (hourMatch) {
                return new Date(now.getTime() - Number(hourMatch[1]) * 60 * 60 * 1000);
            }
            if (/昨天/.test(value)) {
                const parsed = new Date(now);
                parsed.setDate(parsed.getDate() - 1);
                const timeMatch = value.match(/(\d{1,2}):(\d{2})/);
                if (timeMatch) {
                    parsed.setHours(Number(timeMatch[1]), Number(timeMatch[2]), 0, 0);
                }
                return parsed;
            }
            const monthDayMatch = value.match(/(\d{1,2})[月/-](\d{1,2})[日]?\s*(\d{1,2}:\d{2})?/);
            if (monthDayMatch) {
                const parsed = new Date(now.getFullYear(), Number(monthDayMatch[1]) - 1, Number(monthDayMatch[2]));
                if (monthDayMatch[3]) {
                    const [hour, minute] = monthDayMatch[3].split(":").map(Number);
                    parsed.setHours(hour, minute, 0, 0);
                }
                return parsed;
            }
            const direct = new Date(value);
            return Number.isNaN(direct.getTime()) ? null : direct;
        },

        isWithinRecentDays(date, days = 3) {
            if (!date) {
                return true;
            }
            return Date.now() - date.getTime() <= days * 24 * 60 * 60 * 1000;
        },

        getChatListItemMeta(item) {
            const hrName = this.cleanMessage(
                item.querySelector(".name-text")?.textContent ||
                item.querySelector(".name")?.textContent ||
                ""
            );
            const companyName = this.cleanMessage(
                item.querySelector(".name-box span:nth-child(2)")?.textContent ||
                item.querySelector(".company-name")?.textContent ||
                ""
            );
            const preview = this.cleanMessage(
                item.querySelector(".last-msg")?.textContent ||
                item.querySelector(".text")?.textContent ||
                item.querySelector(".message-text")?.textContent ||
                ""
            );
            const timeText = this.cleanMessage(
                item.querySelector(".time")?.textContent ||
                item.querySelector(".date")?.textContent ||
                item.querySelector(".last-time")?.textContent ||
                ""
            );
            return {
                hrName,
                companyName,
                preview,
                timeText,
                lastMessageAt: this.parseBossRelativeTime(timeText),
                hrKey: `${hrName}-${companyName}`.toLowerCase(),
            };
        },

        extractJobIdFromCurrentChat() {
            const href = (
                document.querySelector('a[href*="/job_detail/"]')?.getAttribute("href") ||
                document.querySelector('[href*="/job_detail/"]')?.getAttribute("href") ||
                ""
            );
            const match = href.match(/job_detail\/([^./?]+)\.html/);
            return match ? match[1] : "";
        },

        getLastTimelineMessageByRole(role) {
            const timeline = this.getConversationTimeline();
            for (let index = timeline.length - 1; index >= 0; index -= 1) {
                if (timeline[index].role === role) {
                    return timeline[index];
                }
            }
            return null;
        },

        buildRecentChatReplyPayload(meta) {
            const chatContext = this.getCurrentChatContext();
            const jobId = this.extractJobIdFromCurrentChat();
            const timeline = this.getConversationTimeline();
            const lastMessage = timeline.length ? timeline[timeline.length - 1] : null;
            const lastHrMessage = this.getLastTimelineMessageByRole("HR");
            const companyName = chatContext.companyName || meta.companyName || "";
            const hrName = chatContext.hrName || meta.hrName || "";
            const jobTitle = this.getPositionName() || "";
            const communicationKey = this.buildCommunicationKey({
                platform: "BOSS",
                jobId,
                companyName,
                hrName,
            });
            const lastMessageAt = new Date().toISOString();
            return {
                platform: "BOSS",
                jobId: jobId || communicationKey,
                communicationKey,
                jobTitle,
                companyName,
                companyLogo: chatContext.companyLogo || "",
                companyIndustry: chatContext.companyIndustry || "",
                companySize: chatContext.companySize || "",
                jobLocation: chatContext.jobLocation || "",
                salaryRange: chatContext.salaryRange || "",
                salaryRangeNormalized: this.normalizeSalaryRange(chatContext.salaryRange || ""),
                jobUrl: location.href,
                hrName,
                hrKey: meta.hrKey || this.currentMonitoredHR || "",
                hrTitle: chatContext.hrTitle || "",
                lastMessageContent: lastMessage?.content || meta.preview || "",
                lastMessageRole: lastMessage?.role || "",
                lastMessageAt,
                conversationTimeline: JSON.stringify(timeline.slice(-10)),
                sourcePayload: JSON.stringify({
                    type: "recent-visible-chat-sync",
                    communicationKey,
                    meta,
                    hasHrReply: Boolean(lastHrMessage),
                    lastHrMessage,
                    syncedAt: lastMessageAt,
                }),
            };
        },

        async syncCurrentChatReplySummary(meta) {
            const timeline = this.getConversationTimeline();
            const lastHrMessage = this.getLastTimelineMessageByRole("HR");
            if (!lastHrMessage) {
                return false;
            }
            const payload = this.buildRecentChatReplyPayload(meta);
            if (!payload?.jobId) {
                return false;
            }
            await this.requestCommunicationApi("/api/job-communications/upsert", "POST", payload);
            if (this.isNotSuitableReply(lastHrMessage?.content || payload.lastMessageContent || "")) {
                await this.markJobStatus(payload.jobId, "NOT_SUITABLE");
            } else {
                await this.markJobAsReplied(payload.jobId);
            }
            this.log(`已同步三天内 HR 回复：${payload.companyName || "未知公司"} - ${payload.hrName || "未知HR"}`);
            return Boolean(timeline.length);
        },

        async syncRecentVisibleChatRepliesIfNeeded() {
            const interval = 3 * 60 * 1000;
            if (state.recentChatReplySyncing) {
                return;
            }
            if (Date.now() - state.lastRecentChatReplySyncAt < interval) {
                return;
            }
            await this.syncRecentVisibleChatReplies();
        },

        async syncRecentVisibleChatReplies() {
            if (!settings.communicationSyncEnabled || !location.pathname.includes("/chat")) {
                return;
            }
            const items = this.getVisibleChatListItems();
            if (!items.length) {
                return;
            }
            state.recentChatReplySyncing = true;
            state.lastRecentChatReplySyncAt = Date.now();
            const originalHrKey = this.currentMonitoredHR;
            try {
                let synced = 0;
                const recentItems = items
                    .map((item) => ({ item, meta: this.getChatListItemMeta(item) }))
                    .filter(({ meta }) => this.isWithinRecentDays(meta.lastMessageAt, 3))
                    .slice(0, 20);
                for (const { item, meta } of recentItems) {
                    await this.simulateClick(item.querySelector(".figure") || item);
                    await this.delay(CONFIG.OPERATION_INTERVAL);
                    this.currentMonitoredHR = meta.hrKey || this.buildHrConversationKey();
                    const didSync = await this.syncCurrentChatReplySummary(meta);
                    if (didSync) {
                        synced += 1;
                    }
                    await this.delay(CONFIG.DELAYS.MEDIUM_SHORT);
                }
                if (synced > 0) {
                    this.log(`最近三天可见聊天回复同步完成：${synced} 条`);
                }
            } catch (error) {
                this.log(`最近三天聊天回复同步失败: ${error.message}`);
            } finally {
                this.currentMonitoredHR = originalHrKey;
                state.recentChatReplySyncing = false;
            }
        },

        normalizeCommunicationBaseUrl() {
            return String(settings.communicationBaseUrl || "")
                .trim()
                .replace(/\/$/, "");
        },

        async ensureCommunicationToken() {
            if (String(settings.communicationToken || "").trim()) {
                return settings.communicationToken;
            }

            if (this.communicationTokenBridgePromise) {
                return this.communicationTokenBridgePromise;
            }

            this.communicationTokenBridgePromise = (async () => {
                const origins = deriveBridgeOrigins(this.normalizeCommunicationBaseUrl());
                let lastError = null;

                for (const origin of origins) {
                    try {
                        setCommunicationTestStatus(`正在尝试自动获取后台 token：${origin}`, "info");
                        const payload = await requestTokenFromBridgeOrigin(origin);
                        const accessToken = String(payload?.accessToken || "").trim();
                        if (accessToken) {
                            settings.communicationToken = accessToken;
                            if (state.settings) {
                                state.settings.communicationToken = accessToken;
                            }

                            const tokenInput = document.getElementById("communication-token-input");
                            if (tokenInput) {
                                tokenInput.value = accessToken;
                            }

                            saveSettings();
                            setCommunicationTestStatus(`已自动获取后台 token：${origin}`, "success");
                            return accessToken;
                        }
                        lastError = new Error(`bridge 未登录或无 accessToken: ${origin}`);
                    } catch (error) {
                        lastError = error;
                    }
                }

                if (lastError) {
                    throw lastError;
                }

                return "";
            })();

            try {
                return await this.communicationTokenBridgePromise;
            } finally {
                this.communicationTokenBridgePromise = null;
            }
        },

        isCommunicationSyncReady() {
            return Boolean(
                settings.communicationSyncEnabled &&
                this.normalizeCommunicationBaseUrl()
            );
        },

        async requestCommunicationApi(path, method = "GET", payload = null) {
            if (!settings.communicationSyncEnabled || !this.normalizeCommunicationBaseUrl()) {
                return null;
            }

            if (!this.isCommunicationSyncReady()) {
                throw new Error("缺少有效的后端地址");
            }

            const baseUrl = this.normalizeCommunicationBaseUrl();

            return new Promise((resolve, reject) => {
                GM_xmlhttpRequest({
                    method,
                    url: `${baseUrl}${path}`,
                    timeout: CONFIG.API.TIMEOUT,
                    headers: {
                        "Content-Type": "application/json",
                    },
                    data: payload ? JSON.stringify(payload) : undefined,
                    onload: (response) => {
                        try {
                            const result = JSON.parse(response.responseText || "{}");
                            if (response.status < 200 || response.status >= 300) {
                                throw new Error(result?.message || `HTTP ${response.status}`);
                            }
                            if (result.code !== undefined && result.code !== 200) {
                                throw new Error(result.message || "请求失败");
                            }
                            resolve(result.data);
                        } catch (error) {
                            reject(error);
                        }
                    },
                    ontimeout: () => reject(new Error("请求超时")),
                    onerror: (error) => reject(new Error("网络请求失败: " + JSON.stringify(error))),
                });
            });
        },

        buildCommunicationPayloadFromCard(card) {
            if (!card) {
                return null;
            }
            const extracted = this.extractJobInfo(card);
            if (!extracted || !extracted.jobId) {
                return null;
            }
            const chatContext = this.getCurrentChatContext();
            const rawJobTitle = this.getCardJobTitle(card);
            const rawCompanyName = this.getCardCompanyName(card);
            const rawJobLocation = this.getCardLocation(card);
            const rawSalaryRange = this.getCardSalaryRange(card);
            const jobTitle = rawJobTitle || this.getPositionName() || "";
            const companyName = rawCompanyName || chatContext.companyName || "";
            const rawHrName = this.cleanHrName(this.getCardHrName(card), companyName);
            const jobLocation = rawJobLocation || chatContext.jobLocation || "";
            const salaryRange = rawSalaryRange || chatContext.salaryRange || "";
            const timeline = this.getConversationTimeline();
            const hrName = this.cleanHrName(chatContext.hrName, companyName) || rawHrName || "";
            const communicationKey = this.buildCommunicationKey({
                platform: "BOSS",
                jobId: extracted.jobId,
                companyName,
                hrName,
            });
            return {
                platform: "BOSS",
                jobId: extracted.jobId,
                communicationKey,
                jobTitle,
                companyName,
                companyLogo: chatContext.companyLogo || "",
                companyIndustry: chatContext.companyIndustry || "",
                companySize: chatContext.companySize || "",
                jobLocation,
                salaryRange,
                salaryRangeNormalized: this.normalizeSalaryRange(salaryRange),
                jobUrl: this.getFirstHrefBySelectors(card, ['a[href*="/job_detail/"]']) || "",
                hrName,
                hrKey: this.currentMonitoredHR || "",
                hrTitle: chatContext.hrTitle || "",
                lastMessageContent: timeline.length ? timeline[timeline.length - 1].content : "",
                lastMessageRole: timeline.length ? timeline[timeline.length - 1].role : "CANDIDATE",
                lastMessageAt: new Date().toISOString(),
                conversationTimeline: JSON.stringify(timeline),
                sourcePayload: JSON.stringify({
                    extracted,
                    communicationKey,
                    title: jobTitle,
                    companyName,
                    rawJobTitle,
                    rawCompanyName,
                    rawJobLocation,
                    rawSalaryRange,
                    rawHrName,
                    rawCardText: this.normalizeCardText(card.innerText || card.textContent || "").slice(0, 1000),
                    chatContext,
                    companyIndustry: chatContext.companyIndustry || "",
                    companySize: chatContext.companySize || "",
                    hrTitle: chatContext.hrTitle || "",
                    timeline,
                }),
            };
        },

        buildCommunicationPayloadFromJobInfo(jobInfo) {
            if (!jobInfo?.jobId) {
                return null;
            }
            const chatContext = this.getCurrentChatContext();
            const timeline = this.getConversationTimeline();
            const lastTimelineMessage = timeline.length ? timeline[timeline.length - 1] : null;
            const salaryRange = chatContext.salaryRange || jobInfo.salaryRange || "";
            const companyName = chatContext.companyName || jobInfo.companyName || "";
            const hrName = chatContext.hrName || jobInfo.hrName || "";
            const communicationKey = jobInfo.communicationKey || this.buildCommunicationKey({
                platform: "BOSS",
                jobId: jobInfo.jobId,
                companyName,
                hrName,
            });
            return {
                platform: "BOSS",
                jobId: jobInfo.jobId,
                communicationKey,
                jobTitle: this.getPositionName() || jobInfo.jobTitle || "",
                companyName,
                companyLogo: chatContext.companyLogo || jobInfo.companyLogo || "",
                companyIndustry: chatContext.companyIndustry || jobInfo.companyIndustry || "",
                companySize: chatContext.companySize || jobInfo.companySize || "",
                jobLocation: chatContext.jobLocation || jobInfo.jobLocation || "",
                salaryRange,
                salaryRangeNormalized: this.normalizeSalaryRange(salaryRange),
                jobUrl: jobInfo.jobUrl || location.href,
                hrName,
                hrKey: this.currentMonitoredHR || jobInfo.hrKey || "",
                hrTitle: chatContext.hrTitle || jobInfo.hrTitle || "",
                lastMessageContent: lastTimelineMessage?.content || "",
                lastMessageRole: lastTimelineMessage?.role || "HR",
                lastMessageAt: new Date().toISOString(),
                conversationTimeline: JSON.stringify(timeline),
                sourcePayload: JSON.stringify({
                    extracted: jobInfo,
                    communicationKey,
                    chatContext,
                    timeline,
                }),
            };
        },

        async syncCommunicatedJob(card) {
            try {
                const payload = this.buildCommunicationPayloadFromCard(card);
                if (!payload) {
                    return;
                }
                const salaryDiagnostics = this.getCardSalaryDiagnostics(card);
                const jobIdPart = payload.jobId ? ` ｜ jobId=${payload.jobId}` : "";
                this.log(`即将上报岗位：${payload.jobTitle || "未知岗位"} ｜ ${payload.companyName || "未知公司"} ｜ ${payload.salaryRange || "未获取"} ｜ ${payload.hrName || "未知HR"}${jobIdPart}`);
                if (!payload.salaryRange) {
                    this.log(
                        `薪资提取诊断：选择器命中=${JSON.stringify(salaryDiagnostics.selectorHits)}；文本匹配=${JSON.stringify(salaryDiagnostics.regexMatches)}；卡片文本=${salaryDiagnostics.cardText}`
                    );
                }
                const syncedRecord = await this.requestCommunicationApi("/api/job-communications/upsert", "POST", payload);
                this.log(`岗位沟通记录同步成功：ID=${syncedRecord?.id || "未知"}，状态=${syncedRecord?.status || "未知"}`);
            } catch (error) {
                this.log(`同步岗位沟通记录失败: ${error.message}`);
            }
        },

        async syncRepliedJob(jobInfo) {
            try {
                const payload = this.buildCommunicationPayloadFromJobInfo(jobInfo);
                if (!payload) {
                    return;
                }
                await this.requestCommunicationApi("/api/job-communications/upsert", "POST", payload);
            } catch (error) {
                this.log(`同步回复沟通详情失败: ${error.message}`);
            }
        },

        async markJobAsReplied(jobId) {
            if (!jobId) {
                return;
            }
            try {
                await this.requestCommunicationApi(
                    `/api/job-communications/${encodeURIComponent(jobId)}/reply?platform=BOSS`,
                    "POST"
                );
            } catch (error) {
                this.log(`同步岗位回复状态失败: ${error.message}`);
            }
        },

        async markJobStatus(jobId, status) {
            if (!jobId || !status) {
                return;
            }
            try {
                await this.requestCommunicationApi(
                    `/api/job-communications/${encodeURIComponent(jobId)}/status?platform=BOSS&status=${encodeURIComponent(status)}`,
                    "POST"
                );
            } catch (error) {
                this.log(`同步岗位状态失败: ${error.message}`);
            }
        },

        async filterOutRepliedJobs(jobCards) {
            if (!this.isCommunicationSyncReady() || !Array.isArray(jobCards) || jobCards.length === 0) {
                return jobCards;
            }
            try {
                const mapped = jobCards.map((card) => ({
                    card,
                    jobInfo: this.extractJobInfo(card),
                }));
                const jobIds = mapped
                    .map((item) => item.jobInfo?.jobId)
                    .filter(Boolean);
                if (jobIds.length === 0) {
                    return jobCards;
                }
                const skipped = await this.requestCommunicationApi(
                    "/api/job-communications/skip-check",
                    "POST",
                    { platform: "BOSS", jobIds }
                );
                const skippedIds = new Set((skipped || []).map((item) => item.jobId));
                return mapped
                    .filter((item) => !item.jobInfo?.jobId || !skippedIds.has(item.jobInfo.jobId))
                    .map((item) => item.card);
            } catch (error) {
                this.log(`服务端跳过检查失败: ${error.message}`);
                return jobCards;
            }
        },

        async simulateClick(element) {
            if (!element) return;

            const rect = element.getBoundingClientRect();
            const x = rect.left + rect.width / 2;
            const y = rect.top + rect.height / 2;

            const dispatchMouseEvent = (type, options = {}) => {
                const event = new MouseEvent(type, {
                    bubbles: true,
                    cancelable: true,
                    view: document.defaultView,
                    clientX: x,
                    clientY: y,
                    ...options,
                });
                element.dispatchEvent(event);
            };

            dispatchMouseEvent("mouseover");
            await this.delay(CONFIG.DELAYS.SHORT);
            dispatchMouseEvent("mousemove");
            await this.delay(CONFIG.DELAYS.SHORT);
            dispatchMouseEvent("mousedown", { button: 0 });
            await this.delay(CONFIG.DELAYS.SHORT);
            dispatchMouseEvent("mouseup", { button: 0 });
            await this.delay(CONFIG.DELAYS.SHORT);
            dispatchMouseEvent("click", { button: 0 });
        },

        async waitForElement(selectorOrFunction, timeout = 5000) {
            return new Promise((resolve) => {
                let element;
                if (typeof selectorOrFunction === "function")
                    element = selectorOrFunction();
                else element = document.querySelector(selectorOrFunction);

                if (element) return resolve(element);

                const timeoutId = setTimeout(() => {
                    observer.disconnect();
                    resolve(null);
                }, timeout);
                const observer = new MutationObserver(() => {
                    if (typeof selectorOrFunction === "function")
                        element = selectorOrFunction();
                    else element = document.querySelector(selectorOrFunction);
                    if (element) {
                        clearTimeout(timeoutId);
                        observer.disconnect();
                        resolve(element);
                    }
                });
                observer.observe(document.body, { childList: true, subtree: true });
            });
        },

        getContextMultiplier(context) {
            const multipliers = {
                dict_load: 1.0,
                click: 0.8,
                selection: 0.8,
                default: 1.0,
            };
            return multipliers[context] || multipliers["default"];
        },

        async smartDelay(baseTime, context = "default") {
            const multiplier = this.getContextMultiplier(context);
            const adjustedTime = baseTime * multiplier;
            return this.delay(adjustedTime);
        },

        async delay(ms) {
            return new Promise((resolve) => setTimeout(resolve, ms));
        },

        async handleGreetSettingsPage() {
            try {
                localStorage.setItem(STORAGE.VISITED_GREET_SET, "true");

                await this.delay(1000);

                const titleElement = document.querySelector("h3.title-wrap");

                if (titleElement) {
                    titleElement.textContent = "请务必打开 打招呼语功能";
                    titleElement.style.color = "red";
                    titleElement.style.fontWeight = "bold";
                    titleElement.style.fontSize = "18px";
                }

                const possibleSelectors = [
                    "h4 .ui-switch",
                    ".ui-switch",
                    "span.ui-switch",
                    "[class*='ui-switch']"
                ];

                let switchElement = null;
                for (const selector of possibleSelectors) {
                    switchElement = document.querySelector(selector);
                    if (switchElement) {
                        break;
                    }
                }

                if (switchElement) {
                    const isChecked = switchElement.classList.contains("ui-switch-checked");
                    if (!isChecked) {
                        await this.simulateClick(switchElement);
                        await this.delay(800);

                        const newSwitchElement = document.querySelector(possibleSelectors.find(s => document.querySelector(s)));
                        if (newSwitchElement && newSwitchElement.classList.contains("ui-switch-checked")) {
                            UI.notify("招呼语功能已启用", "success");
                        } else {
                            await this.simulateClick(switchElement);
                            await this.delay(500);

                            const finalSwitchElement = document.querySelector(possibleSelectors.find(s => document.querySelector(s)));
                            if (finalSwitchElement && finalSwitchElement.classList.contains("ui-switch-checked")) {
                                UI.notify("招呼语功能已启用", "success");
                            } else {
                                UI.notify("请手动启用招呼语功能", "warning");
                            }
                        }
                    } else {
                        UI.notify("招呼语功能已启用", "success");
                    }
                } else {
                    const allSwitches = document.querySelectorAll("[class*='switch']");
                    allSwitches.forEach((el, index) => {
                        this.log(`开关 ${index + 1}: ${el.className}, 文本: ${el.textContent?.trim()}`);
                    });
                }
            } catch (error) {
                ErrorHandler.handle(error, 'Core.handleGreetSettingsPage');
            }
        },

        extractTwoCharKeywords(text) {
            const keywords = [];
            const cleanedText = text.replace(/[\s,，.。:：;；""''\[\]\(\)\{\}]/g, "");

            for (let i = 0; i < cleanedText.length - 1; i++) {
                keywords.push(cleanedText.substring(i, i + 2));
            }

            return keywords;
        },

        resetCycle() {
            toggleProcess();
            this.log("所有岗位沟通完成，恭喜您即将找到理想工作！");
            state.currentIndex = 0;
        },

        markJobDelivered() {
            state.session.deliveredCount += 1;
            state.session.lastActionWasDelivery = true;

            const limit = getJobApplyLimit();
            if (limit > 0) {
                this.log(`本轮已投递 ${state.session.deliveredCount}/${limit}`);
            } else {
                this.log(`本轮已投递 ${state.session.deliveredCount} 次`);
            }

            if (limit > 0 && state.session.deliveredCount >= limit) {
                this.log(`达到停止次数 ${limit}，已自动停止海投`);
                if (state.isRunning) {
                    toggleProcess();
                }
            }
        },

        buildHrConversationKey() {
            const name = (
                document.querySelector(".name-text")?.textContent ||
                document.querySelector(".boss-info-attr .name")?.textContent ||
                ""
            ).trim();
            const company = (
                document.querySelector(".name-box span:nth-child(2)")?.textContent ||
                document.querySelector(".company-name")?.textContent ||
                document.querySelector(".company-text")?.textContent ||
                ""
            ).trim();
            return `${name}-${company}`.toLowerCase();
        },

        log(message) {
            const logEntry = `[${new Date().toLocaleTimeString()}] ${message}`;
            const logPanel = document.querySelector("#pro-log");
            if (logPanel) {
                if (state.comments.isCommentMode) {
                    return;
                }

                const logItem = document.createElement("div");
                logItem.className = "log-item";
                logItem.style.padding = "0px 8px";
                logItem.textContent = logEntry;
                logPanel.appendChild(logItem);
                logPanel.scrollTop = logPanel.scrollHeight;
            }
        },
















    };

    function toggleProcess() {
        try {
            state.isRunning = !state.isRunning;

            if (state.isRunning) {
                state.comments.isCommentMode = false;
                state.jobList = [];
                state.processedJobIds = new Set();
                state.session.deliveredCount = 0;
                state.session.lastActionWasDelivery = false;

                state.includeKeywords = (elements.includeInput?.value || "")
                    .trim()
                    .toLowerCase()
                    .split(/[，,]/)
                    .filter((keyword) => keyword.trim() !== "");
                state.locationKeywords = (elements.locationInput?.value || "")
                    .trim()
                    .toLowerCase()
                    .split(/[，,]/)
                    .filter((keyword) => keyword.trim() !== "");
                state.excludeCompanyKeywords = (elements.excludeCompanyInput?.value || "")
                    .trim()
                    .toLowerCase()
                    .split(/[，,]/)
                    .filter((keyword) => keyword.trim() !== "");
                settings.outsourcingKeywords = (elements.outsourcingKeywordsInput?.value || "")
                    .trim()
                    .toLowerCase()
                    .split(/[，,]/)
                    .filter((keyword) => keyword.trim() !== "");
                settings.excludeOutsourcing = Boolean(
                    elements.excludeOutsourcingToggle?.checked
                );

                if (elements.jobApplyIntervalSelect) {
                    settings.jobApplyInterval = parseNonNegativeInt(
                        elements.jobApplyIntervalSelect.value,
                        5000
                    );
                }
                if (elements.jobApplyLimitInput) {
                    settings.jobApplyLimit = parseNonNegativeInt(
                        elements.jobApplyLimitInput.value,
                        0
                    );
                    elements.jobApplyLimitInput.value = String(getJobApplyLimit());
                }
                saveSettings();

                elements.controlBtn.textContent = "停止海投";
                elements.controlBtn.style.background = "#4F46E5";

                const logPanel = document.querySelector("#pro-log");
                if (logPanel) {
                    logPanel.innerHTML = "";
                }

                const startTime = new Date();
                Core.log(`开始自动海投，时间：${startTime.toLocaleTimeString()}`);
                Core.log(
                    `筛选条件：职位名包含【${state.includeKeywords.join("、") || "无"
                    }】，工作地包含【${state.locationKeywords.join("、") || "无"}】，排除公司【${state.excludeCompanyKeywords.join("、") || "无"}】`
                );
                Core.log(
                    `投递节奏：${getJobApplyInterval() / 1000}秒/次，停止次数：${
                        getJobApplyLimit() > 0 ? getJobApplyLimit() : "不限"
                    }`
                );

                Core.startProcessing();
            } else {
                elements.controlBtn.textContent = settings.aiGreetingEnabled
                    ? "🚀 一键投递"
                    : "▶ 启动海投";
                elements.controlBtn.style.background = "#4F46E5";

                state.isRunning = false;
                state.currentIndex = 0;
                state.jobList = [];
                state.processedJobIds = new Set();
                state.session.deliveredCount = 0;
                state.session.lastActionWasDelivery = false;

                if (location.pathname.includes("/jobs")) {
                    setTimeout(() => {
                        Core.loadAndDisplayComments();
                    }, 300);
                }
            }
        } catch (error) {
            state.isRunning = false;
            state.jobList = [];
            state.processedJobIds = new Set();
            if (elements.controlBtn) {
                elements.controlBtn.textContent = settings.aiGreetingEnabled
                    ? "🚀 一键投递"
                    : "▶ 启动海投";
                elements.controlBtn.style.background = "#4F46E5";
            }
            ErrorHandler.handle(error, "toggleProcess");
            Core.log(`启动海投失败：${error.message}`);
        }
    }

    function toggleChatProcess() {
        state.isRunning = !state.isRunning;

        if (state.isRunning) {
            elements.controlBtn.textContent = "停止智能聊天";
            elements.controlBtn.style.background = "#34a853";

            const startTime = new Date();
            Core.log(`开始智能聊天，时间：${startTime.toLocaleTimeString()}`);

            Core.startProcessing();
        } else {
            elements.controlBtn.textContent = "开始智能聊天";
            elements.controlBtn.style.background = "#34a853";

            state.isRunning = false;

            if (Core.messageObserver) {
                Core.messageObserver.disconnect();
                Core.messageObserver = null;
            }

            const stopTime = new Date();
            Core.log(`停止智能聊天，时间：${stopTime.toLocaleTimeString()}`);
        }
    }

    const STORAGE = {
        LETTER: "letterLastShown",
        GUIDE: "shouldShowGuide",
        AI_COUNT: "aiReplyCount",
        AI_DATE: "lastAiDate",
        VISITED_GREET_SET: "hasVisitedGreetSet",
    };

    const letter = {
        showLetterToUser: function () {
            const COLORS = {
                primary: "#4F46E5",
                text: "#333",
                textLight: "#666",
                background: "#f8f9fa",
            };

            const overlay = document.createElement("div");
            overlay.id = "letter-overlay";
            overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.7);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
            backdrop-filter: blur(5px);
            animation: fadeIn 0.3s ease-out;
        `;

            const envelopeContainer = document.createElement("div");
            envelopeContainer.id = "envelope-container";
            envelopeContainer.style.cssText = `
            position: relative;
            width: 90%;
            max-width: 650px;
            height: 400px;
            perspective: 1000px;
        `;

            const envelope = document.createElement("div");
            envelope.id = "envelope";
            envelope.style.cssText = `
            position: absolute;
            width: 100%;
            height: 100%;
            transform-style: preserve-3d;
            transition: transform 0.6s ease;
        `;

            const envelopeBack = document.createElement("div");
            envelopeBack.id = "envelope-back";
            envelopeBack.style.cssText = `
            position: absolute;
            width: 100%;
            height: 100%;
            background: ${COLORS.background};
            border-radius: 10px;
            box-shadow: 0 15px 35px rgba(0,0,0,0.2);
            backface-visibility: hidden;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 30px;
            cursor: pointer;
            transition: all 0.3s;
        `;
            envelopeBack.innerHTML = `
            <div style="font-size:clamp(1.5rem, 3vw, 1.8rem);font-weight:600;color:${COLORS.primary};margin-bottom:10px;">
                <i class="fa fa-envelope-o mr-2"></i>致海投用户的一封信
            </div>
            <div style="font-size:clamp(1rem, 2vw, 1.1rem);color:${COLORS.textLight};text-align:center;">
                点击开启高效求职之旅
            </div>
            <div style="position:absolute;bottom:20px;font-size:0.85rem;color:#999;">
                © ${new Date().getFullYear()} BOSS海投助手 | Zion Cai 版权所有
            </div>
        `;

            envelopeBack.addEventListener("click", () => {
                envelope.style.transform = "rotateY(180deg)";
                setTimeout(() => {
                    const content = document.getElementById("letter-content");
                    if (content) {
                        content.style.display = "block";
                        content.style.animation = "fadeInUp 0.5s ease-out forwards";
                    }
                }, 300);
            });

            const envelopeFront = document.createElement("div");
            envelopeFront.id = "envelope-front";
            envelopeFront.style.cssText = `
            position: absolute;
            width: 100%;
            height: 100%;
            background: #fff;
            border-radius: 10px;
            box-shadow: 0 15px 35px rgba(0,0,0,0.2);
            transform: rotateY(180deg);
            backface-visibility: hidden;
            display: flex;
            flex-direction: column;
        `;

            const titleBar = document.createElement("div");
            titleBar.style.cssText = `
            padding: 20px 30px;
            background: #4F46E5;
            color: white;
            font-size: clamp(1.2rem, 2.5vw, 1.4rem);
            font-weight: 600;
            border-radius: 10px 10px 0 0;
            display: flex;
            align-items: center;
        `;
            titleBar.innerHTML = `<i class="fa fa-envelope-open-o mr-2"></i>致海投助手用户：`;

            const letterContent = document.createElement("div");
            letterContent.id = "letter-content";
            letterContent.style.cssText = `
            flex: 1;
            padding: 25px 30px;
            overflow-y: auto;
            font-size: clamp(0.95rem, 2vw, 1.05rem);
            line-height: 1.8;
            color: ${COLORS.text};

            background-blend-mode: overlay;
            background-color: rgba(255,255,255,0.95);
            display: none;
        `;
            letterContent.innerHTML = `
            <div style="margin-bottom:20px;">
                <p>展信佳。</p>
                <p class="mt-2">&emsp;&emsp;正在困扰于实习面试的朋友：</p>
                <p class="mt-2">&emsp;&emsp;你好。</p>
                <p class="mt-3">
                    &emsp;&emsp;我也许和你一样，是在求职路上奔波的学生。经历过简历石沉大海、面试大脑空白的时刻，也懂得发出消息却无人回应的焦虑。这些重复与内耗，我深有体会。
                </p>
                <p class="mt-3">
                    &emsp;&emsp;正因如此，我用Claude Code做出了这个小工具。它不替代你的努力，只愿帮你节省时间，让你更专注于真正重要的事。它或许可以为你做这些：
                </p>
                <ul class="mt-3 ml-6 list-disc" style="text-indent:0;">
                    <li>&emsp;&emsp;自动沟通页面岗位，一键打招呼</li>
                    <li>&emsp;&emsp;AI智能生成契合岗位的招呼语，让每一次沟通都更有温度</li>
                    <li>&emsp;&emsp;个性化沟通策略，让HR更愿意回应你</li>
                </ul>
                <p class="mt-3">
                    &emsp;&emsp;希望这个工具帮你省下重复操作的时间，去专注真正重要的事。求职如长跑，保持节奏比冲刺更关键。愿你方向清晰，步履从容，最终抵达的不仅是理想岗位，更是更坚定、更舒展的自己。
                </p>
                <p class="mt-2">
                    &emsp;&emsp;祝前行有光，脚下有路。
                </p>
            </div>
            <div style="text-align:right;color:${COLORS.textLight};text-indent:0;">
                你的伙伴<br>
                ZionCai<br>
                2026年6月于西安
            </div>
        `;

            const buttonArea = document.createElement("div");
            buttonArea.style.cssText = `
            padding: 15px 30px;
            display: flex;
            justify-content: center;
            border-top: 1px solid #eee;
            background: ${COLORS.background};
            border-radius: 0 0 10px 10px;
        `;

            const startButton = document.createElement("button");
            startButton.style.cssText = `
            background: #4F46E5;
            color: white;
            border: none;
            border-radius: 8px;
            padding: 12px 30px;
            font-size: clamp(1rem, 2vw, 1.1rem);
            font-weight: 500;
            cursor: pointer;
            transition: all 0.3s;
            box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3);
            outline: none;
            display: flex;
            align-items: center;
        `;
            startButton.innerHTML = `<i class="fa fa-rocket mr-2"></i>开始使用`;

            startButton.addEventListener("click", () => {
                const hasVisitedGreetSet = localStorage.getItem(STORAGE.VISITED_GREET_SET);

                if (!hasVisitedGreetSet) {
                    localStorage.setItem(STORAGE.VISITED_GREET_SET, "true");
                    window.open(
                        "https://www.zhipin.com/web/geek/notify-set?type=greetSet",
                        "_blank"
                    );
                }

                envelopeContainer.style.animation = "scaleOut 0.3s ease-in forwards";
                overlay.style.animation = "fadeOut 0.3s ease-in forwards";
                setTimeout(() => {
                    if (overlay.parentNode === document.body) {
                        document.body.removeChild(overlay);
                    }
                }, 300);
            });

            buttonArea.appendChild(startButton);
            envelopeFront.appendChild(titleBar);
            envelopeFront.appendChild(letterContent);
            envelopeFront.appendChild(buttonArea);
            envelope.appendChild(envelopeBack);
            envelope.appendChild(envelopeFront);
            envelopeContainer.appendChild(envelope);
            overlay.appendChild(envelopeContainer);
            document.body.appendChild(overlay);

            const style = document.createElement("style");
            style.textContent = `
            @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
            @keyframes fadeOut { from { opacity: 1 } to { opacity: 0 } }
            @keyframes scaleOut { from { transform: scale(1); opacity: 1 } to { transform: scale(.9); opacity: 0 } }
            @keyframes fadeInUp { from { opacity: 0; transform: translateY(20px) } to { opacity: 1; transform: translateY(0) } }

            #envelope-back:hover { transform: scale(1.02); box-shadow: 0 20px 40px rgba(0,0,0,0.25); }
            #envelope-front button:hover { transform: scale(1.05); box-shadow: 0 8px 20px rgba(79, 70, 229, 0.4); }
            #envelope-front button:active { transform: scale(0.98); }

            @media (max-width: 480px) {
                #envelope-container { height: 350px; }
                #letter-content { font-size: 0.9rem; padding: 15px; }
            }
        `;
            document.head.appendChild(style);
        },
    };

    const guide = {
        steps: [
            {
                target: "div.city-label.active",
                content:
                    '海投前，先在BOSS<span class="highlight">筛选出岗位</span>！\n\n助手会先滚动收集界面上显示的岗位，\n随后依次进行沟通~',

                arrowPosition: "bottom",
                defaultPosition: {
                    left: "50%",
                    top: "20%",
                    transform: "translateX(-50%)",
                },
            },
            {
                target: 'a[ka="header-jobs"]',
                content:
                    '<span class="highlight">职位页操作流程</span>：\n\n1. 扫描职位卡片\n2. 点击"立即沟通"（需开启"自动打招呼"）\n3. 留在当前页，继续沟通下一个职位\n\n全程无需手动干预，高效投递！',

                arrowPosition: "bottom",
                defaultPosition: { left: "25%", top: "80px" },
            },
            {
                target: 'a[ka="header-message"]',
                content:
                    '<span class="highlight">海投建议</span>！\n\n• HR与您沟通，HR需要付费给平台\n因此您尽可能先自我介绍以提高效率 \n\n• HR查看附件简历，HR也要付费给平台\n所以尽量先发送`图片简历`给HR',

                arrowPosition: "left",
                defaultPosition: { right: "150px", top: "100px" },
            },
            {
                target: "div.logo",
                content:
                    '<span class="highlight">您需要打开两个浏览器窗口</span>：\n\n左侧窗口自动打招呼发起沟通\n右侧发送自我介绍和图片简历\n\n您只需专注于挑选offer！',

                arrowPosition: "right",
                defaultPosition: { left: "200px", top: "20px" },
            },
            {
                target: "div.logo",
                content:
                    '<span class="highlight">特别注意</span>：\n\n1. <span class="warning">BOSS直聘每日打招呼上限为150次</span>\n2. 聊天页仅处理最上方的最新对话\n3. 打招呼后对方会显示在聊天页\n4. <span class="warning">投递操作过于频繁有封号风险!</span>',

                arrowPosition: "bottom",
                defaultPosition: { left: "50px", top: "80px" },
            },
        ],
        currentStep: 0,
        guideElement: null,
        overlay: null,
        highlightElements: [],

        showGuideToUser() {
            this.overlay = document.createElement("div");
            this.overlay.id = "guide-overlay";
            this.overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            backdrop-filter: blur(2px);
            z-index: 99997;
            pointer-events: none;
            opacity: 0;
            transition: opacity 0.3s ease;
        `;
            document.body.appendChild(this.overlay);

            this.guideElement = document.createElement("div");
            this.guideElement.id = "guide-tooltip";
            this.guideElement.style.cssText = `
            position: fixed;
            z-index: 99999;
            width: 320px;
            background: white;
            border-radius: 12px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            overflow: hidden;
            opacity: 0;
            transform: translateY(10px);
            transition: opacity 0.3s ease, transform 0.3s ease;
        `;
            document.body.appendChild(this.guideElement);

            setTimeout(() => {
                this.overlay.style.opacity = "1";

                setTimeout(() => {
                    this.showStep(0);
                }, 300);
            }, 100);
        },

        showStep(stepIndex) {
            const step = this.steps[stepIndex];
            if (!step) return;

            this.clearHighlights();
            const target = document.querySelector(step.target);

            if (target) {
                const rect = target.getBoundingClientRect();
                const highlight = document.createElement("div");
                highlight.className = "guide-highlight";
                highlight.style.cssText = `
                position: fixed;
                top: ${rect.top}px;
                left: ${rect.left}px;
                width: ${rect.width}px;
                height: ${rect.height}px;
                background: ${step.highlightColor || "#4F46E5"};
                opacity: 0.2;
                border-radius: 4px;
                z-index: 99998;
                box-shadow: 0 0 0 4px ${step.highlightColor || "#4F46E5"};
                animation: guide-pulse 2s infinite;
            `;
                document.body.appendChild(highlight);
                this.highlightElements.push(highlight);

                this.setGuidePositionFromTarget(step, rect);
            } else {
                console.warn("引导目标元素未找到，使用默认位置:", step.target);

                this.setGuidePositionFromDefault(step);
            }

            let buttonsHtml = "";

            if (stepIndex === this.steps.length - 1) {
                buttonsHtml = `
                <div class="guide-buttons" style="display: flex; justify-content: center; padding: 16px; border-top: 1px solid #f0f0f0; background: #f9fafb;">
                    <button id="guide-finish-btn" style="padding: 8px 32px; background: ${step.highlightColor || "#4F46E5"
                }; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; transition: all 0.2s ease; box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);">
                        完成
                    </button>
                </div>
            `;
            } else {
                buttonsHtml = `
                <div class="guide-buttons" style="display: flex; justify-content: flex-end; padding: 16px; border-top: 1px solid #f0f0f0; background: #f9fafb;">
                    <button id="guide-skip-btn" style="padding: 8px 16px; background: white; color: #4b5563; border: 1px solid #e5e7eb; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; transition: all 0.2s ease;">跳过</button>
                    <button id="guide-next-btn" style="padding: 8px 16px; background: ${step.highlightColor || "#4F46E5"
                }; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; margin-left: 8px; transition: all 0.2s ease; box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);">下一步</button>
                </div>
            `;
            }

            this.guideElement.innerHTML = `
            <div class="guide-header" style="padding: 16px; background: ${step.highlightColor || "#4F46E5"
            }; color: white;">
                <div class="guide-title" style="font-size: 16px; font-weight: 600;">海投助手引导</div>
                <div class="guide-step" style="font-size: 12px; opacity: 0.8; margin-top: 2px;">步骤 ${stepIndex + 1
            }/${this.steps.length}</div>
            </div>
            <div class="guide-content" style="padding: 20px; font-size: 14px; line-height: 1.6;">
                <div style="white-space: pre-wrap; font-family: inherit; margin: 0;">${step.content
            }</div>
            </div>
            ${buttonsHtml}
        `;

            if (stepIndex === this.steps.length - 1) {
                document
                    .getElementById("guide-finish-btn")
                    .addEventListener("click", () => this.endGuide(true));
            } else {
                document
                    .getElementById("guide-next-btn")
                    .addEventListener("click", () => this.nextStep());
                document
                    .getElementById("guide-skip-btn")
                    .addEventListener("click", () => this.endGuide());
            }

            if (stepIndex === this.steps.length - 1) {
                const finishBtn = document.getElementById("guide-finish-btn");
                finishBtn.addEventListener("mouseenter", () => {
                    finishBtn.style.background = this.darkenColor(
                        step.highlightColor || "#4F46E5",
                        15
                    );
                    finishBtn.style.boxShadow =
                        "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)";
                });
                finishBtn.addEventListener("mouseleave", () => {
                    finishBtn.style.background = step.highlightColor || "#4F46E5";
                    finishBtn.style.boxShadow =
                        "0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)";
                });
            } else {
                const nextBtn = document.getElementById("guide-next-btn");
                const skipBtn = document.getElementById("guide-skip-btn");

                nextBtn.addEventListener("mouseenter", () => {
                    nextBtn.style.background = this.darkenColor(
                        step.highlightColor || "#4F46E5",
                        15
                    );
                    nextBtn.style.boxShadow =
                        "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)";
                });
                nextBtn.addEventListener("mouseleave", () => {
                    nextBtn.style.background = step.highlightColor || "#4F46E5";
                    nextBtn.style.boxShadow =
                        "0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)";
                });

                skipBtn.addEventListener("mouseenter", () => {
                    skipBtn.style.background = "#f3f4f6";
                });
                skipBtn.addEventListener("mouseleave", () => {
                    skipBtn.style.background = "white";
                });
            }

            this.guideElement.style.opacity = "1";
            this.guideElement.style.transform = "translateY(0)";
        },

        setGuidePositionFromTarget(step, rect) {
            let left, top;
            const guideWidth = 320;
            const guideHeight = 240;

            switch (step.arrowPosition) {
                case "top":
                    left = rect.left + rect.width / 2 - guideWidth / 2;
                    top = rect.top - guideHeight - 20;
                    break;
                case "bottom":
                    left = rect.left + rect.width / 2 - guideWidth / 2;
                    top = rect.bottom + 20;
                    break;
                case "left":
                    left = rect.left - guideWidth - 20;
                    top = rect.top + rect.height / 2 - guideHeight / 2;
                    break;
                case "right":
                    left = rect.right + 20;
                    top = rect.top + rect.height / 2 - guideHeight / 2;
                    break;
                default:
                    left = rect.right + 20;
                    top = rect.top;
            }

            left = Math.max(10, Math.min(left, window.innerWidth - guideWidth - 10));
            top = Math.max(10, Math.min(top, window.innerHeight - guideHeight - 10));

            this.guideElement.style.left = `${left}px`;
            this.guideElement.style.top = `${top}px`;
            this.guideElement.style.transform = "translateY(0)";
        },

        setGuidePositionFromDefault(step) {
            const position = step.defaultPosition || {
                left: "50%",
                top: "50%",
                transform: "translate(-50%, -50%)",
            };

            Object.assign(this.guideElement.style, {
                left: position.left,
                top: position.top,
                right: position.right || "auto",
                bottom: position.bottom || "auto",
                transform: position.transform || "none",
            });
        },

        nextStep() {
            const currentStep = this.steps[this.currentStep];
            if (currentStep) {
                const target = document.querySelector(currentStep.target);
                if (target) {
                    target.removeEventListener("click", this.nextStep);
                }
            }

            this.currentStep++;
            if (this.currentStep < this.steps.length) {
                this.guideElement.style.opacity = "0";
                this.guideElement.style.transform = "translateY(10px)";

                setTimeout(() => {
                    this.showStep(this.currentStep);
                }, 300);
            }
        },

        clearHighlights() {
            this.highlightElements.forEach((el) => el.remove());
            this.highlightElements = [];
        },

        endGuide(isCompleted = false) {
            this.clearHighlights();

            this.guideElement.style.opacity = "0";
            this.guideElement.style.transform = "translateY(10px)";
            this.overlay.style.opacity = "0";

            setTimeout(() => {
                if (this.overlay && this.overlay.parentNode) {
                    this.overlay.parentNode.removeChild(this.overlay);
                }
                if (this.guideElement && this.guideElement.parentNode) {
                    this.guideElement.parentNode.removeChild(this.guideElement);
                }

                if (isCompleted && this.chatUrl) {
                    window.open(this.chatUrl, "_blank");
                }
            }, 300);

            document.dispatchEvent(new Event("guideEnd"));
        },

        darkenColor(color, percent) {
            let R = parseInt(color.substring(1, 3), 16);
            let G = parseInt(color.substring(3, 5), 16);
            let B = parseInt(color.substring(5, 7), 16);

            R = parseInt((R * (100 - percent)) / 100);
            G = parseInt((G * (100 - percent)) / 100);
            B = parseInt((B * (100 - percent)) / 100);

            R = R < 255 ? R : 255;
            G = G < 255 ? G : 255;
            B = B < 255 ? B : 255;

            R = Math.round(R);
            G = Math.round(G);
            B = Math.round(B);

            const RR =
                R.toString(16).length === 1 ? "0" + R.toString(16) : R.toString(16);
            const GG =
                G.toString(16).length === 1 ? "0" + G.toString(16) : G.toString(16);
            const BB =
                B.toString(16).length === 1 ? "0" + B.toString(16) : B.toString(16);

            return `#${RR}${GG}${BB}`;
        },
    };

    const style = document.createElement("style");
    style.textContent = `
    @keyframes guide-pulse {
        0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(79, 70, 229, 0.4); }
        70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(79, 70, 229, 0); }
        100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(79, 70, 229, 0); }
    }

    .guide-content .highlight {
        font-weight: 700;
        color: #1a73e8;
    }

    .guide-content .warning {
        font-weight: 700;
        color: #d93025;
    }
`;
    document.head.appendChild(style);

    function getToday() {
        return new Date().toISOString().split("T")[0];
    }

    async function init() {
        try {
            APIInterceptor.init();


            const midnight = new Date();
            midnight.setDate(midnight.getDate() + 1);
            midnight.setHours(0, 0, 0, 0);
            setTimeout(() => {
                localStorage.removeItem(STORAGE.AI_COUNT);
                localStorage.removeItem(STORAGE.AI_DATE);
                localStorage.removeItem(STORAGE.LETTER);
            }, midnight - Date.now());
            UI.init();
            document.body.style.position = "relative";
            const today = getToday();
            if (location.pathname.includes("/jobs")) {
                if (localStorage.getItem(STORAGE.LETTER) !== today) {
                    letter.showLetterToUser();
                    localStorage.setItem(STORAGE.LETTER, today);
                } else if (localStorage.getItem(STORAGE.GUIDE) !== "true") {
                    guide.showGuideToUser();
                    localStorage.setItem(STORAGE.GUIDE, "true");
                }
                Core.log("欢迎使用海投助手，我将自动投递岗位！");
            } else if (location.pathname.includes("/chat")) {
                Core.log("欢迎使用海投助手，我将自动发送简历！");
            } else if (location.pathname.includes("/notify-set")) {
                Core.log("欢迎使用海投助手，我将自动启用招呼语功能！");
                Core.handleGreetSettingsPage();
            } else {
                Core.log("当前页面暂不支持，请移步至职位页面！");
            }
        } catch (error) {
            console.error("初始化失败:", error);
            alert("ERR: "+error.message);
        }
    }

    init();

    let lastUrl = location.href;
    new MutationObserver(() => {
        const currentUrl = location.href;
        if (currentUrl !== lastUrl) {
            lastUrl = currentUrl;
            if (UI.currentPageType === UI.PAGE_TYPES.JOB_LIST && !state.isRunning && location.pathname.includes("/jobs")) {
                setTimeout(() => {
                    Core.loadAndDisplayComments();
                }, 500);
            }
        }
    }).observe(document, { subtree: true, childList: true });

    function addGreetingItem() {
        if (!state.settings.greetingsList) {
            state.settings.greetingsList = [];
        }

        const hasEmpty = state.settings.greetingsList.some(
            (greeting) => !greeting.content.trim()
        );

        if (hasEmpty) {
            return;
        }

        const newGreeting = {
            id: Date.now().toString(),
            content: "",
        };

        state.settings.greetingsList.push(newGreeting);
        StatePersistence.saveState();
        renderGreetingsList();
    }

    function renderGreetingsList() {
        const greetingsList = document.getElementById("greetings-list");
        if (!greetingsList) return;

        greetingsList.innerHTML = "";

        if (
            !state.settings.greetingsList ||
            state.settings.greetingsList.length === 0
        ) {
            greetingsList.innerHTML =
                '<div style="color: #6b7280; text-align: center; padding: 20px;">暂无自我介绍内容</div>';
            return;
        }

        state.settings.greetingsList.forEach((greeting, index) => {
            const greetingElement = document.createElement("div");
            greetingElement.className = "greeting-item";
            greetingElement.style.cssText = `
        border: 1px solid #e5e7eb;
        border-radius: 6px;
        padding: 6px;
        margin-bottom: 6px;
        background: #f9fafb;
      `;

            greetingElement.innerHTML = `
        <div style="display: flex; gap: 8px; align-items: center;">
          <span style="color: #6b7280; font-size: 12px; min-width: 20px;">${index + 1}.</span>
          <div style="flex: 1;">
            <input type="text" class="greeting-input" data-id="${greeting.id}" value="${greeting.content}" placeholder="输入自我介绍内容" style="
              width: 100%;
              padding: 4px 6px;
              border: 1px solid #d1d5db;
              border-radius: 3px;
              font-size: 13px;
            ">
          </div>
          <button class="delete-greeting-btn" data-id="${greeting.id}" style="
            padding: 3px 6px;
            border: 1px solid #ef4444;
            background: #fef2f2;
            color: #dc2626;
            border-radius: 3px;
            font-size: 11px;
            cursor: pointer;
            white-space: nowrap;
          ">删除</button>
        </div>
      `;

            greetingsList.appendChild(greetingElement);
        });

        attachGreetingEventListeners();
    }

    function attachGreetingEventListeners() {
        document.querySelectorAll(".delete-greeting-btn").forEach((btn) => {
            btn.addEventListener("click", (e) => {
                const greetingId = e.target.dataset.id;
                state.settings.greetingsList = state.settings.greetingsList.filter(
                    (g) => g.id !== greetingId
                );
                StatePersistence.saveState();
                renderGreetingsList();
            });
        });

        document.querySelectorAll(".greeting-input").forEach((input) => {
            input.addEventListener("input", (e) => {
                const greetingId = e.target.dataset.id;
                const greeting = state.settings.greetingsList.find(
                    (g) => g.id === greetingId
                );
                if (greeting) {
                    greeting.content = e.target.value;
                    StatePersistence.saveState();
                }
            });
        });
    }

    function loadGreetings() {
        if (!state.settings.greetingsList) {
            state.settings.greetingsList = [];
        }
        renderGreetingsList();
    }

    function loadSettingsIntoUI() {
        if (elements.includeInput) {
            elements.includeInput.value = state.includeKeywords.join("，");
        }

        if (elements.locationInput) {
            elements.locationInput.value = state.locationKeywords.join("，");
        }

        if (elements.excludeCompanyInput) {
            elements.excludeCompanyInput.value = state.excludeCompanyKeywords.join("，");
        }

        if (elements.excludeOutsourcingToggle) {
            elements.excludeOutsourcingToggle.checked = settings.excludeOutsourcing;
        }

        if (elements.outsourcingKeywordsInput) {
            elements.outsourcingKeywordsInput.value = (
                settings.outsourcingKeywords || CONFIG.DEFAULT_OUTSOURCING_KEYWORDS
            ).join("，");
        }

        const aiRoleInput = document.getElementById("ai-role-input");
        if (aiRoleInput) {
            aiRoleInput.value = settings.ai.role;
        }

        const aiApiUrlInput = document.getElementById("ai-api-url-input");
        if (aiApiUrlInput) {
            aiApiUrlInput.value = settings.ai.apiUrl || "";
        }

        const aiApiKeyInput = document.getElementById("ai-api-key-input");
        if (aiApiKeyInput) {
            aiApiKeyInput.value = settings.ai.apiKey || "";
        }

        const aiModelInput = document.getElementById("ai-model-input");
        if (aiModelInput) {
            aiModelInput.value = settings.ai.model || "";
        }

        const communicationBaseUrlInput = document.getElementById("communication-base-url-input");
        if (communicationBaseUrlInput) {
            communicationBaseUrlInput.value = settings.communicationBaseUrl || "";
        }

        const communicationTokenInput = document.getElementById("communication-token-input");
        if (communicationTokenInput) {
            communicationTokenInput.value = settings.communicationToken || "";
        }

        const communicationSyncEnabledInput = document.getElementById("communication-sync-enabled-input");
        if (communicationSyncEnabledInput) {
            communicationSyncEnabledInput.checked = !!settings.communicationSyncEnabled;
        }

        const autoReplyInput = document.querySelector(
            "#toggle-auto-reply-mode input"
        );
        if (autoReplyInput) {
            autoReplyInput.checked = settings.autoReply;
        }

        const autoSendResumeInput = document.querySelector(
            "#toggle-auto-send-resume input"
        );
        if (autoSendResumeInput) {
            autoSendResumeInput.checked = settings.useAutoSendResume;
        }

        const excludeHeadhuntersInput = document.querySelector(
            "#toggle-exclude-headhunters input"
        );
        if (excludeHeadhuntersInput) {
            excludeHeadhuntersInput.checked = settings.excludeHeadhunters;
        }

        const autoSendImageResumeInput = document.querySelector(
            "#toggle-auto-send-image-resume input"
        );
        if (autoSendImageResumeInput) {
            autoSendImageResumeInput.checked =
                settings.useAutoSendImageResume &&
                settings.imageResumes &&
                settings.imageResumes.length > 0;
        }

        const userResumeInput = document.getElementById("user-resume-input");
        if (userResumeInput) {
            userResumeInput.value = settings.resume || "";
        }

        const greetingTemplateInput = document.getElementById(
            "greeting-template-input"
        );
        if (greetingTemplateInput) {
            greetingTemplateInput.value = settings.greetingTemplate || "";
        }

        if (elements.jobApplyIntervalSelect) {
            elements.jobApplyIntervalSelect.value = String(getJobApplyInterval());
        }

        if (elements.jobApplyLimitInput) {
            elements.jobApplyLimitInput.value = String(getJobApplyLimit());
        }

        loadGreetings();
        updateStatusOptions();
    }
})();
