import request from './request'

const DEFAULT_COMPANY_NAME = '大禾种业'

export async function loadLegalCompanyProfile() {
  try {
    const data = await request.get('/miniapp/public/company-intro')
    const companyInfo = (data && data.companyInfo) || {}
    const contacts = Array.isArray(data && data.contacts) ? data.contacts : []
    return {
      companyName: String(companyInfo.companyName || DEFAULT_COMPANY_NAME).trim() || DEFAULT_COMPANY_NAME,
      contacts
    }
  } catch (error) {
    console.error('load legal company profile failed', error)
    return {
      companyName: DEFAULT_COMPANY_NAME,
      contacts: []
    }
  }
}

export function buildUserAgreementSections(companyName = DEFAULT_COMPANY_NAME) {
  return [
    {
      title: '一、协议适用范围',
      content: [
        `本《用户服务协议》适用于你使用“${companyName}”微信小程序及其相关服务的全部过程。`,
        '当你访问、浏览、申请认证、登录或实际使用本小程序功能时，即表示你已阅读并愿意遵守本协议。'
      ]
    },
    {
      title: '二、服务内容',
      content: [
        '本小程序主要提供田块查看、农事记录、批次管理、检测记录、位置与天气辅助等农业生产相关功能。',
        '具体功能以小程序实际展示和业务开放范围为准，平台可基于业务需要对功能进行调整、升级或下线。'
      ]
    },
    {
      title: '三、认证与账号使用',
      content: [
        '你应按照页面要求提交真实、准确、可识别的认证资料。',
        '通过审核后，你可使用对应微信账号登录本小程序；未经授权，不得冒用他人身份、借用他人账号或协助他人绕过审核。'
      ]
    },
    {
      title: '四、用户承诺',
      content: [
        '你承诺在使用过程中遵守法律法规及平台规则，不上传违法、侵权、失实、含有敏感信息或与业务无关的内容。',
        '你应确保提交的田块、农事、图片、批次、检测等信息来源合法、内容真实，并对相应后果负责。'
      ]
    },
    {
      title: '五、平台使用规范',
      content: [
        '本小程序仅用于约定业务场景，禁止用于攻击、抓取、批量滥用接口、绕过权限控制或其他影响系统稳定运行的行为。',
        '如发现异常使用、越权访问或违规内容，平台有权限制功能、停用账号或追究责任。'
      ]
    },
    {
      title: '六、服务变更、中断与终止',
      content: [
        '因系统维护、升级、网络波动、不可抗力或监管要求，服务可能临时中断、调整或终止。',
        '对于因上述原因导致的短时不可用，平台会尽量提前提示或在恢复后继续提供服务。'
      ]
    },
    {
      title: '七、联系方式',
      content: [
        `如你对本协议或小程序服务有疑问，可通过“${companyName}”对外公示的联系方式与我们联系。`
      ]
    }
  ]
}

export function buildPrivacyPolicySections(companyName = DEFAULT_COMPANY_NAME) {
  return [
    {
      title: '一、我们收集的信息',
      content: [
        '为完成认证、登录和业务记录，我们可能收集你主动填写或授权提供的姓名、昵称、手机号、头像等信息。',
        '为实现田块定位、天气辅助和现场记录，我们可能在你授权后收集位置信息、上传的图片文件及与业务相关的填写内容。'
      ]
    },
    {
      title: '二、信息使用目的',
      content: [
        '我们收集上述信息，仅用于身份识别、业务记录、权限校验、问题排查、数据追溯和功能优化。',
        '未经你的授权或法律法规允许，我们不会将你的个人信息用于与本小程序业务无关的用途。'
      ]
    },
    {
      title: '三、权限与接口说明',
      content: [
        '当业务需要获取微信登录凭证、位置信息、头像、图片上传等能力时，我们会在对应场景向你说明用途，并在你同意后再调用。',
        '若你拒绝授权，相关依赖能力的功能可能无法正常使用，但不会影响与之无关的基础浏览能力。'
      ]
    },
    {
      title: '四、信息存储与保护',
      content: [
        '我们会在实现业务目的所必需的期限内保存你的相关信息，并采取合理措施保护数据安全。',
        '对于图片、定位、农事记录等业务数据，我们会根据实际管理要求进行存储、审核、备份与访问控制。'
      ]
    },
    {
      title: '五、信息共享与披露',
      content: [
        '除法律法规要求、监管机关要求、你单独授权或业务必要协同外，我们不会向无关第三方共享你的个人信息。',
        '如确需与受托服务方协作处理相关数据，我们会要求其按照不低于本政策的标准承担保密和安全义务。'
      ]
    },
    {
      title: '六、你的权利',
      content: [
        '你可以通过页面操作或联系管理员申请更正、更新相关信息；你也可以停止使用本小程序。',
        '如你对信息处理有疑问、投诉或建议，可通过本政策列示的联系方式与我们联系。'
      ]
    },
    {
      title: '七、政策更新',
      content: [
        `如${companyName}对本隐私政策进行调整，我们会通过适当方式进行提示；更新后的政策生效后，将适用于后续服务使用。`
      ]
    }
  ]
}
