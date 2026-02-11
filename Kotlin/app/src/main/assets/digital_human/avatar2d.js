(() => {
  const statusEl = document.getElementById('status');
  const btnNod = document.getElementById('btnNod');
  const canvas = document.getElementById('c');
  const ctx = canvas.getContext('2d');

  function setStatus(msg) {
    statusEl.textContent = msg;
    console.log('[digital_human_2d]', msg);
  }

  // ====== 配置你的 PNG 序列帧文件名 ======
  const FRAME_BASE = './frames/';

  const IDLE_FRAME_FILES = [
    'idle_0001.png', 'idle_0002.png', 'idle_0003.png', 'idle_0004.png', 'idle_0005.png', 'idle_0006.png', 'idle_0007.png', 'idle_0008.png', 'idle_0009.png', 'idle_0010.png',
    'idle_0011.png', 'idle_0012.png', 'idle_0013.png', 'idle_0014.png', 'idle_0015.png', 'idle_0016.png', 'idle_0017.png', 'idle_0018.png', 'idle_0019.png', 'idle_0020.png',
    'idle_0021.png', 'idle_0022.png', 'idle_0023.png', 'idle_0024.png', 'idle_0025.png', 'idle_0026.png', 'idle_0027.png', 'idle_0028.png', 'idle_0029.png', 'idle_0030.png',
    'idle_0031.png', 'idle_0032.png', 'idle_0033.png', 'idle_0034.png', 'idle_0035.png', 'idle_0036.png', 'idle_0037.png', 'idle_0038.png', 'idle_0039.png', 'idle_0040.png',
    'idle_0041.png', 'idle_0042.png', 'idle_0043.png', 'idle_0044.png', 'idle_0045.png', 'idle_0046.png', 'idle_0047.png', 'idle_0048.png', 'idle_0049.png', 'idle_0050.png',
    'idle_0051.png', 'idle_0052.png', 'idle_0053.png', 'idle_0054.png', 'idle_0055.png', 'idle_0056.png', 'idle_0057.png', 'idle_0058.png', 'idle_0059.png', 'idle_0060.png',
  ];

  const THOUGHT_FRAME_FILES = Array.from({ length: 60 }, (_, i) => `thought_${String(i + 1).padStart(4, '0')}.png`);

  const TALK_FRAME_FILES = [
    'output_0001.png', 'output_0002.png', 'output_0003.png', 'output_0004.png', 'output_0005.png', 'output_0006.png', 'output_0007.png', 'output_0008.png', 'output_0009.png', 'output_0010.png',
    'output_0011.png', 'output_0012.png', 'output_0013.png', 'output_0014.png', 'output_0015.png', 'output_0016.png', 'output_0017.png', 'output_0018.png', 'output_0019.png', 'output_0020.png',
    'output_0021.png', 'output_0022.png', 'output_0023.png', 'output_0024.png', 'output_0025.png', 'output_0026.png', 'output_0027.png', 'output_0028.png', 'output_0029.png', 'output_0030.png',
    'output_0031.png', 'output_0032.png', 'output_0033.png', 'output_0034.png', 'output_0035.png', 'output_0036.png', 'output_0037.png', 'output_0038.png', 'output_0039.png', 'output_0040.png',
    'output_0041.png', 'output_0042.png', 'output_0043.png', 'output_0044.png', 'output_0045.png', 'output_0046.png', 'output_0047.png', 'output_0048.png', 'output_0049.png', 'output_0050.png',
    'output_0051.png', 'output_0052.png', 'output_0053.png', 'output_0054.png', 'output_0055.png', 'output_0056.png', 'output_0057.png', 'output_0058.png', 'output_0059.png', 'output_0060.png',
  ];

    const idleFrames = IDLE_FRAME_FILES.map((name) => {
    const img = new Image();
    img.src = FRAME_BASE + name;
    return img;
  });

  const thoughtFrames = THOUGHT_FRAME_FILES.map((name) => {
    const img = new Image();
    img.src = FRAME_BASE + name;
    return img;
  });

  const talkFrames = TALK_FRAME_FILES.map((name) => {
    const img = new Image();
    img.src = FRAME_BASE + name;
    return img;
  });

  let allLoaded = false;
  function waitForImagesLoaded(images, cb) {
    let remaining = images.length;
    if (!remaining) {
      cb();
      return;
    }
    images.forEach((img) => {
      if (img.complete && img.naturalWidth > 0) {
        if (--remaining === 0) cb();
      } else {
        img.onload = () => {
          if (--remaining === 0) cb();
        };
        img.onerror = () => {
          console.warn('图片加载失败:', img.src);
          if (--remaining === 0) cb();
        };
      }
    });
  }

  // ===== 音频能量，用于“说话”时切换/加速 =====
  let audioEl = null;
  let audioCtx = null;
  let analyser = null;
  let dataArray = null;

  function ensureAudioGraph() {
    if (audioCtx) return;
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 256;
    dataArray = new Uint8Array(analyser.frequencyBinCount);
  }

  function connectAudioElement(el) {
    ensureAudioGraph();
    const src = audioCtx.createMediaElementSource(el);
    src.connect(analyser);
    analyser.connect(audioCtx.destination);
  }

  function getRms01() {
    if (!analyser || !dataArray) return 0;
    analyser.getByteTimeDomainData(dataArray);
    let sum = 0;
    for (let i = 0; i < dataArray.length; i++) {
      const v = (dataArray[i] - 128) / 128;
      sum += v * v;
    }
    return Math.min(1, Math.sqrt(sum / dataArray.length) * 4);
  }

  // ===== 画布尺寸和缩放 =====
  function resizeCanvas() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  // 思考标记（等待语音时）
  let isThinking = false;

  // ===== 动画状态 =====
  let currentFrames = idleFrames;
  let currentIndex = 0;
  let lastFrameTime = 0;
  const idleFps = 10;
  const talkFpsBase = 12;

  // 点头
  let nodUntil = 0;
  btnNod.addEventListener('click', () => {
    nodUntil = performance.now() + 500;
  });

  function drawFrame(timestamp) {
    if (!allLoaded) {
      requestAnimationFrame(drawFrame);
      return;
    }

    const w = canvas.clientWidth || window.innerWidth;
    const h = canvas.clientHeight || window.innerHeight;

    ctx.clearRect(0, 0, w, h);

    const speaking = audioEl && !audioEl.paused && !audioEl.ended;
    const rms = speaking ? getRms01() : 0;

    // 根据说话状态切换帧序列
    // 始终在 audioEl 播放期间使用 talk 序列，避免 talk/idle 交叉
    currentFrames = speaking ? talkFrames : isThinking ? thoughtFrames : idleFrames;

    const frameDuration = 1000 / (speaking ? talkFpsBase + rms * 10 : idleFps);
    if (timestamp - lastFrameTime > frameDuration) {
      lastFrameTime = timestamp;
      currentIndex = (currentIndex + 1) % currentFrames.length;
    }

    const img = currentFrames[currentIndex];
    if (img && img.complete && img.naturalWidth > 0) {
      const targetH = h * 0.7;
      const scale = targetH / img.naturalHeight;
      const drawW = img.naturalWidth * scale;
      const drawH = img.naturalHeight * scale;
      let x = (w - drawW) / 2;
      let y = (h - drawH) / 2;

      // 点头 / 轻微上下晃动
      const now = performance.now();
      let offsetY = 0;
      if (nodUntil > now) {
        const p = 1 - (nodUntil - now) / 500;
        offsetY = Math.sin(p * Math.PI) * 10; // 点头动画
      } else if (speaking) {
        // 说话期间不进行额外上下晃动，避免整体抖动
        offsetY = 0;
      } else {
        // 静止时做轻微呼吸感晃动
        offsetY = Math.sin(timestamp / 800) * 3;
      }
      y += offsetY;

      ctx.drawImage(img, x, y, drawW, drawH);
    }

    requestAnimationFrame(drawFrame);
  }

  function bootstrap() {
    if (!IDLE_FRAME_FILES.length) {
      setStatus('请先在 avatar2d.js 配置 IDLE_FRAME_FILES / TALK_FRAME_FILES（你的 PNG 序列）。');
      return;
    }

    setStatus('初始化 2D 数字人中...');

    const allImages = [...idleFrames, ...talkFrames, ...thoughtFrames];
    waitForImagesLoaded(allImages, () => {
      allLoaded = true;
      setStatus('2D 数字人已就绪');
    });

    requestAnimationFrame(drawFrame);
  }

  // 暴露给 Android 的接口（保持不变）
  window.avatar = {
    setThinking: (flag) => { isThinking = !!flag; },
    say: async (text, audioUrl) => {
      try {
        setStatus(`准备说话: ${String(text).slice(0, 30)}...`);

        if (!audioEl) {
          audioEl = document.createElement('audio');
          audioEl.preload = 'auto';
          audioEl.crossOrigin = 'anonymous';
          audioEl.addEventListener('ended', () => setStatus('空闲'));
          audioEl.addEventListener('error', (e) => {
            console.error('音频播放错误:', e);
            setStatus('音频播放失败');
          });
          document.body.appendChild(audioEl);
          connectAudioElement(audioEl);
        }

        if (audioCtx && audioCtx.state === 'suspended') {
          try {
            await audioCtx.resume();
          } catch (e) {
            console.warn('恢复 AudioContext 失败:', e);
          }
        }

        audioEl.src = audioUrl;
        await audioEl.play();
        setStatus('说话中...');
      } catch (e) {
        console.error('说话失败:', e);
        setStatus('说话失败: ' + (e.message || String(e)));
      }
    },
  };

  bootstrap();
})();

