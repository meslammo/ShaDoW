const SAFE_COMMANDS = [
  { re: /^(?:what(?:'s| is)\s+)?(?:the )?time\b|الساعة\b|الوقت\b/i, run: () => new Date().toLocaleTimeString('en-GB') },
  { re: /(?:what(?:'s| is)\s+)?(?:today(?:'s)? date|date)\b|التاريخ\b|النهارده\b|اليوم\b/i, run: () => new Date().toLocaleDateString('en-GB') },
  { re: /(?:calculate|compute|احسب|كام)\b/i, run: null },
  { re: /^(?:hi|hello|hey|اهلا|أهلا|سلام|هاي)\b/i, run: () => 'جاهز يا محمد.' },
  { re: /(?:status|حالة شادو|حالة الجهاز|النظام عامل ايه)/i, run: () => 'Shadow شغال في وضع التشغيل الاحتياطي الداخلي.' },
];

function safeCalc(expr) {
  const x = String(expr || '').replace(/^(?:calculate|compute|احسب|كام)\s*/i, '').trim();
  if (!x || !/^[0-9+\-*/().%\s]+$/.test(x) || x.length > 200) return null;
  try {
    const value = Function('"use strict"; return (' + x + ')')();
    return typeof value === 'number' && Number.isFinite(value) ? String(value) : null;
  } catch { return null; }
}

export function runOffline(message) {
  const text = String(message || '').trim();
  if (!text) return { answer: 'أنا جاهز.', capability: 'offline.core', verified: true };
  const calc = safeCalc(text);
  if (calc !== null) return { answer: calc, capability: 'offline.calculator', verified: true };
  for (const item of SAFE_COMMANDS) {
    if (item.re.test(text)) {
      if (item.run) return { answer: item.run(), capability: 'offline.core', verified: true };
    }
  }
  return {
    answer: 'الاتصال بخدمات الذكاء الاصطناعي غير متاح حاليًا. أقدر أكمل الأوامر المحلية الأساسية وأرجع أونلاين تلقائيًا لما الخدمة ترجع.',
    capability: 'offline.fallback',
    verified: true,
  };
}

export function offlineStatus() {
  return { available: true, capabilities: ['offline.core', 'offline.calculator', 'offline.fallback'] };
}
