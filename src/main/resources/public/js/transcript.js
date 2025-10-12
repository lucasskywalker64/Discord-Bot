document.addEventListener("DOMContentLoaded", async () => {
    document.querySelectorAll('time').forEach(timeElement => {
        const dateString = timeElement.getAttribute('datetime');
        if (dateString) {
            const localDate = new Date(dateString);
            const options = { year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit', second: '2-digit', timeZoneName: 'short' };
            timeElement.textContent = localDate.toLocaleString(undefined, options);
        }
    });

    document.querySelectorAll('.spoiler').forEach(spoiler => {
        spoiler.addEventListener('click', () => spoiler.classList.toggle('revealed'));
    });

    const attachments = document.querySelectorAll('[data-attachment-key]');
    if (attachments.length === 0) return;

    try {
        const ticketId = document.body.dataset.ticketId;
        const response = await fetch(`${ticketId}/attachments`);
        if (!response.ok) throw new Error('Failed to fetch attachment URLs');
        const urls = await response.json();

        const iconPaths = {
            play: '<path fill="currentColor" d="M8 5v14l11-7z"></path>',
            pause: '<path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"></path>',
            volumeHigh: '<path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"></path>',
            volumeLow: '<path fill="currentColor" d="M7 9v6h4l5 5V4L11 9H7z"></path>',
            volumeMute: '<path fill="currentColor" d="M7 9v6h4l5 5V4L11 9H7zM16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3z"></path>',
            default: '<svg viewBox="0 0 98.63 122.88" width="24" height="24"><path fill="currentColor" d="M98.63,35.57A3.58,3.58,0,0,0,96,32.12L64.39,1.32A3.53,3.53,0,0,0,61.63,0H6.43A6.42,6.42,0,0,0,0,6.43v110a6.42,6.42,0,0,0,6.43,6.43H92.21a6.42,6.42,0,0,0,6.4-6.43q0-40.45,0-80.88Zm-33.43-23L86.68,32.69H65.2V12.57ZM7.18,115.7V7.15H58V36.26a3.61,3.61,0,0,0,3.61,3.61H91.45q0,37.92,0,75.83Z"></path></svg>',
            text: '<svg viewBox="0 0 412 511.56" width="24" height="24"><path fill="currentColor" d="M32.24 0h229.59a9.06 9.06 0 016.77 3.04l140.63 136.27a8.971 8.971 0 012.74 6.48h.03V479.32c0 8.83-3.63 16.88-9.47 22.74l-.05.05c-5.86 5.83-13.9 9.45-22.72 9.45H32.24c-8.87 0-16.94-3.63-22.78-9.47C3.63 496.26 0 488.19 0 479.32V32.24C0 23.37 3.63 15.3 9.46 9.46 15.3 3.63 23.37 0 32.24 0zm56.24 414.35c-5.01 0-9.08-4.06-9.08-9.07 0-5.01 4.07-9.08 9.08-9.08h235.04c5.01 0 9.07 4.07 9.07 9.08s-4.06 9.07-9.07 9.07H88.48zm0-74.22c-5.01 0-9.08-4.06-9.08-9.07 0-5.01 4.07-9.08 9.08-9.08h231.38c5.01 0 9.08 4.07 9.08 9.08s-4.07 9.07-9.08 9.07H88.48zm0-74.22c-5.01 0-9.08-4.07-9.08-9.08s4.07-9.07 9.08-9.07H275.7c5.01 0 9.08 4.06 9.08 9.07 0 5.01-4.07 9.08-9.08 9.08H88.48zm0-74.23c-5.01 0-9.08-4.06-9.08-9.07 0-5.01 4.07-9.08 9.08-9.08h114.45c5.01 0 9.07 4.07 9.07 9.08s-4.06 9.07-9.07 9.07H88.48zm0-74.22c-5.01 0-9.08-4.06-9.08-9.07a9.08 9.08 0 019.08-9.08h56.29a9.08 9.08 0 019.08 9.08c0 5.01-4.07 9.07-9.08 9.07H88.48zm176.37-92.85v114.4h118.07L264.85 24.61zm129 132.55H255.78c-5.01 0-9.08-4.07-9.08-9.08V18.15H32.24c-3.86 0-7.39 1.59-9.95 4.15-2.55 2.55-4.14 6.08-4.14 9.94v447.08c0 3.86 1.59 7.39 4.14 9.94 2.56 2.56 6.09 4.15 9.95 4.15h347.52c3.89 0 7.41-1.58 9.94-4.11l.04-.04c2.53-2.53 4.11-6.05 4.11-9.94V157.16z"/></svg>',
            archive: '<svg viewBox="0 0 380 511.43" width="24" height="24"><path fill="currentColor" d="M26.51 0H247.2c1.36 0 2.64.27 3.82.77 2.03.67 3.78 2 4.98 3.73l91.28 108.36 30.4 35.9a9.729 9.729 0 0 1 2.3 6.28l.02 329.88c0 7.23-3.01 13.86-7.81 18.66l-.04.04c-4.8 4.8-11.43 7.81-18.66 7.81H26.51c-7.22 0-13.87-2.99-18.68-7.8l-.04-.04C3 498.79 0 492.18 0 484.92V26.51c0-7.27 2.98-13.89 7.78-18.69l.04-.04C12.62 2.98 19.24 0 26.51 0zm39.51 48.19h56.22v39.25H66.02v39.25h56.22v39.24H66.02v39.25h56.22v39.25h56.22v-39.25h-56.21v-39.25h56.21v-39.24h-56.21V87.44h56.21V48.19h-56.21v-28.7h105.69l-.43 11.49c-4.57 117.65-4.97 128.76 133 134.28v319.66c0 1.89-.81 3.65-2.09 4.93-1.28 1.28-3.04 2.08-4.93 2.08H26.51c-1.9 0-3.67-.79-4.95-2.07-1.27-1.27-2.07-3.03-2.07-4.94V26.51c0-1.93.79-3.7 2.05-4.97a7.065 7.065 0 0 1 4.97-2.05h39.51v28.7zm283.22 97.13-5.94-7.06-96.1-113.47-.27 6.88c-3.74 96.29-4.2 108.6 102.31 113.65zM66.17 257.4h112.15v134.28H66.17V257.4zm21.43 68.51h69.29v42.61H87.6v-42.61z"/></svg>',
            pdf: '<svg viewBox="0 0 102.55 122.88" width="24" height="24"><path fill="currentColor" d="M102.55,122.88H0V0h77.66l24.89,26.43V122.88L102.55,122.88z M87.01,69.83c-1.48-1.46-4.75-2.22-9.74-2.29 c-3.37-0.03-7.43,0.27-11.7,0.86c-1.91-1.1-3.88-2.31-5.43-3.75c-4.16-3.89-7.64-9.28-9.8-15.22c0.14-0.56,0.26-1.04,0.37-1.54 c0,0,2.35-13.32,1.73-17.82c-0.08-0.61-0.14-0.8-0.3-1.27l-0.2-0.53c-0.64-1.47-1.89-3.03-3.85-2.94l-1.18-0.03 c-2.19,0-3.97,1.12-4.43,2.79c-1.42,5.24,0.05,13.08,2.7,23.24l-0.68,1.65c-1.9,4.64-4.29,9.32-6.39,13.44l-0.28,0.53 c-2.22,4.34-4.23,8.01-6.05,11.13l-1.88,1c-0.14,0.07-3.36,1.78-4.12,2.24c-6.41,3.83-10.66,8.17-11.37,11.62 c-0.22,1.1-0.05,2.51,1.08,3.16L17.32,97c0.79,0.4,1.62,0.6,2.47,0.6c4.56,0,9.87-5.69,17.18-18.44 c8.44-2.74,18.04-5.03,26.45-6.29c6.42,3.61,14.3,6.12,19.28,6.12c0.89,0,1.65-0.08,2.27-0.25c0.95-0.26,1.76-0.8,2.25-1.54 c0.96-1.46,1.16-3.46,0.9-5.51c-0.08-0.61-0.56-1.36-1.09-1.88L87.01,69.83L87.01,69.83z M18.79,94.13 c0.83-2.28,4.13-6.78,9.01-10.78c0.3-0.25,1.06-0.95,1.75-1.61C24.46,89.87,21.04,93.11,18.79,94.13L18.79,94.13L18.79,94.13z M47.67,27.64c1.47,0,2.31,3.7,2.38,7.17c0.07,3.47-0.74,5.91-1.75,7.71c-0.83-2.67-1.24-6.87-1.24-9.62 C47.06,32.89,47,27.64,47.67,27.64L47.67,27.64L47.67,27.64z M39.05,75.02c1.03-1.83,2.08-3.76,3.17-5.81 c2.65-5.02,4.32-8.93,5.57-12.15c2.48,4.51,5.57,8.35,9.2,11.42c0.45,0.38,0.93,0.77,1.44,1.15 C51.05,71.09,44.67,72.86,39.05,75.02L39.05,75.02L39.05,75.02L39.05,75.02z M85.6,74.61c-0.45,0.28-1.74,0.44-2.56,0.44 c-2.67,0-5.98-1.22-10.62-3.22c1.78-0.13,3.41-0.2,4.88-0.2c2.68,0,3.48-0.01,6.09,0.66C86.01,72.96,86.05,74.32,85.6,74.61 L85.6,74.61L85.6,74.61L85.6,74.61z M96.12,115.98V30.45H73.44V5.91H6.51v110.07H96.12L96.12,115.98z"/></svg>',
            code: '<svg viewBox="0 0 122.88 101.57" width="24" height="24"><path fill="currentColor" d="M44.97,12.84h-17.2L0,49.37L27.77,85.9h17.2L17.2,49.37L44.97,12.84L44.97,12.84z M77.91,12.84h17.2l27.77,36.53 L95.11,85.9h-17.2l27.77-36.53L77.91,12.84L77.91,12.84z M70.17,0.04l5.96,1.39c0.94,0.22,1.52,1.16,1.31,2.1l-22.5,96.69 c-0.22,0.93-1.16,1.52-2.1,1.31l-5.95-1.39c-0.94-0.22-1.52-1.16-1.31-2.1l22.5-96.69C68.3,0.42,69.24-0.17,70.17,0.04L70.17,0.04 L70.17,0.04z"/></svg>',
            pptx: '<svg viewBox="0 0 115.28" width="24" height="24"><path fill="currentColor" d="M25.38,57h64.88V37.34H69.59c-2.17,0-5.19-1.17-6.62-2.6c-1.43-1.43-2.3-4.01-2.3-6.17V7.64l0,0H8.15 c-0.18,0-0.32,0.09-0.41,0.18C7.59,7.92,7.55,8.05,7.55,8.24v106.45c0,0.14,0.09,0.32,0.18,0.41c0.09,0.14,0.28,0.18,0.41,0.18 c22.78,0,58.09,0,81.51,0c0.18,0,0.17-0.09,0.27-0.18c0.14-0.09,0.33-0.28,0.33-0.41v-11.16H25.38c-4.14,0-7.56-3.4-7.56-7.56 V64.55C17.82,60.4,21.22,57,25.38,57L25.38,57z M29.52,67.41h13.1c2.85,0,4.99,0.68,6.41,2.03c1.42,1.36,2.13,3.29,2.13,5.8 c0,2.57-0.78,4.59-2.33,6.04c-1.55,1.45-3.91,2.18-7.09,2.18h-4.32v9.43h-7.9V67.41L29.52,67.41z M37.42,78.3h1.94 c1.53,0,2.6-0.27,3.22-0.79c0.62-0.53,0.93-1.2,0.93-2.03c0-0.8-0.27-1.48-0.81-2.03c-0.53-0.56-1.54-0.84-3.03-0.84h-2.25V78.3 L37.42,78.3z M54.7,67.41h13.1c2.85,0,4.99,0.68,6.41,2.03c1.42,1.36,2.13,3.29,2.13,5.8c0,2.57-0.78,4.59-2.33,6.04 c-1.55,1.45-3.91,2.18-7.09,2.18H62.6v9.43h-7.9V67.41L54.7,67.41z M62.6,78.3h1.94c1.53,0,2.6-0.27,3.22-0.79 c0.62-0.53,0.93-1.2,0.93-2.03c0-0.8-0.27-1.48-0.81-2.03c-0.53-0.56-1.54-0.84-3.03-0.84H62.6V78.3L62.6,78.3z M78.13,67.41h23.95 v6.3h-8.04v19.18h-7.87V73.71h-8.04V67.41L78.13,67.41z M97.79,57h9.93c4.16,0,7.56,3.41,7.56,7.56v31.42 c0,4.15-3.41,7.56-7.56,7.56h-9.93v13.55c0,1.61-0.65,3.04-1.7,4.1c-1.06,1.06-2.49,1.7-4.1,1.7c-29.44,0-56.59,0-86.18,0 c-1.61,0-3.04-0.64-4.1-1.7c-1.06-1.06-1.7-2.49-1.7-4.1V5.85c0-1.61,0.65-3.04,1.7-4.1c1.06-1.06,2.53-1.7,4.1-1.7h58.72 C64.66,0,64.8,0,64.94,0c0.64,0,1.29,0.28,1.75,0.69h0.09c0.09,0.05,0.14,0.09,0.23,0.18l29.99,30.36c0.51,0.51,0.88,1.2,0.88,1.98 c0,0.23-0.05,0.41-0.09,0.65V57L97.79,57z M67.52,27.97V8.94l21.43,21.7H70.19c-0.74,0-1.38-0.32-1.89-0.78 C67.84,29.4,67.52,28.71,67.52,27.97L67.52,27.97z"/></svg>',
            sheet: '<svg viewBox="0 0 122.88 102.52" width="24" height="24"><path fill="currentColor" d="M5.42,0h112.04c2.98,0,5.42,2.44,5.42,5.42V97.1c0,2.98-2.44,5.42-5.42,5.42H5.42c-2.98,0-5.42-2.44-5.42-5.42 V5.42C0,2.44,2.44,0,5.42,0L5.42,0z M8.48,23.58H38.1c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48H8.48 c-0.81,0-1.48-0.67-1.48-1.48v-9.76C6.99,24.25,7.66,23.58,8.48,23.58L8.48,23.58z M84.78,82.35h29.63c0.82,0,1.48,0.67,1.48,1.48 v9.76c0,0.81-0.67,1.48-1.48,1.48H84.78c-0.81,0-1.48-0.67-1.48-1.48v-9.76C83.29,83.02,83.96,82.35,84.78,82.35L84.78,82.35z M46.8,82.35h29.28c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48H46.8c-0.81,0-1.48-0.67-1.48-1.48v-9.76 C45.31,83.02,45.98,82.35,46.8,82.35L46.8,82.35z M8.48,82.35H38.1c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48 H8.48c-0.81,0-1.48-0.67-1.48-1.48v-9.76C6.99,83.02,7.66,82.35,8.48,82.35L8.48,82.35z M84.78,62.76h29.63 c0.82,0,1.48,0.67,1.48,1.48V74c0,0.81-0.67,1.48-1.48,1.48H84.78c-0.81,0-1.48-0.67-1.48-1.48v-9.76 C83.29,63.43,83.96,62.76,84.78,62.76L84.78,62.76z M46.8,62.76h29.28c0.82,0,1.48,0.67,1.48,1.48V74c0,0.81-0.67,1.48-1.48,1.48 H46.8c-0.81,0-1.48-0.67-1.48-1.48v-9.76C45.31,63.43,45.98,62.76,46.8,62.76L46.8,62.76z M8.48,62.76H38.1 c0.82,0,1.48,0.67,1.48,1.48V74c0,0.81-0.67,1.48-1.48,1.48H8.48c-0.81,0-1.48-0.67-1.48-1.48v-9.76 C6.99,63.43,7.66,62.76,8.48,62.76L8.48,62.76z M84.78,43.17h29.63c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48 H84.78c-0.81,0-1.48-0.67-1.48-1.48v-9.76C83.29,43.84,83.96,43.17,84.78,43.17L84.78,43.17z M46.8,43.17h29.28 c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48H46.8c-0.81,0-1.48-0.67-1.48-1.48v-9.76 C45.31,43.84,45.98,43.17,46.8,43.17L46.8,43.17z M8.48,43.17H38.1c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48 H8.48c-0.81,0-1.48-0.67-1.48-1.48v-9.76C6.99,43.84,7.66,43.17,8.48,43.17L8.48,43.17z M84.78,23.58h29.63 c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48H84.78c-0.81,0-1.48-0.67-1.48-1.48v-9.76 C83.29,24.25,83.96,23.58,84.78,23.58L84.78,23.58z M46.8,23.58h29.28c0.82,0,1.48,0.67,1.48,1.48v9.76c0,0.81-0.67,1.48-1.48,1.48 H46.8c-0.81,0-1.48-0.67-1.48-1.48v-9.76C45.31,24.25,45.98,23.58,46.8,23.58L46.8,23.58z"/></svg>'
        };

        attachments.forEach(element => {
            const key = element.dataset.attachmentKey;
            const url = urls[key];
            if (!url) {
                console.error('No URL found for attachment "' + key + '"');
                return;
            }

            const wrapper = document.createElement('div');
            wrapper.className = 'attachment-wrapper';

            const downloadButton = document.createElement('a');
            downloadButton.className = 'download-button';
            downloadButton.title = 'Download';
            downloadButton.innerHTML = '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"></path></svg>';
            downloadButton.href = url;
            downloadButton.download = element.dataset.filename || '';

            const tagName = element.tagName;

            if (tagName === 'IMG' || tagName === 'VIDEO' || (tagName === 'DIV' && element.classList.contains('attachment-file'))) {
                if (tagName === 'VIDEO') {
                    const source = element.querySelector('source');
                    if (source) source.src = url;
                    element.load();
                } else if (tagName === 'IMG') {
                    element.src = url;
                } else {
                    const fileNameEl = element.querySelector('.file-name');
                    const fileIconEl = element.querySelector('.file-icon');
                    if (fileNameEl && fileIconEl) {
                        const fileTypeMap = {
                            archive: ['zip', 'rar', '7z', 'tar', 'gz'],
                            pdf: ['pdf'],
                            code: ['html', 'css', 'js', 'json', 'xml', 'py', 'java', 'cpp', 'cs', 'bat'],
                            text: ['docx', 'odt', 'rtf', 'txt'],
                            pptx: ['pptx'],
                            sheet: ['xlsx', 'xls', 'xlsm', 'xltx', 'xlsb', 'ods', 'csv', 'tsv']
                        };
                        const extensionToTypeMap = {};
                        for (const type in fileTypeMap) {
                            fileTypeMap[type].forEach(ext => extensionToTypeMap[ext] = type);
                        }
                        const fileName = fileNameEl.textContent.trim();
                        const extension = fileName.slice(fileName.lastIndexOf('.') + 1).toLowerCase();
                        fileIconEl.innerHTML = iconPaths[extensionToTypeMap[extension] || 'default'];
                    }
                }
                element.parentNode.insertBefore(wrapper, element);
                wrapper.appendChild(element);
                wrapper.appendChild(downloadButton);
            } else if (tagName === 'AUDIO') {
                const source = element.querySelector('source');
                if (source) source.src = url;
                element.load();

                const playerUI = element.previousElementSibling;
                if (!playerUI || !playerUI.classList.contains('discord-audio-player')) return;

                const playPauseButton = playerUI.querySelector('.play-pause-button');
                const playPauseIcon = playerUI.querySelector('.play-pause-icon');
                const timeDisplay = playerUI.querySelector('.time-display');
                const progressBar = playerUI.querySelector('.progress-bar');
                const progressBarWrapper = playerUI.querySelector('.progress-bar-wrapper');
                const volumeButton = playerUI.querySelector('.volume-button');
                const volumeIcon = playerUI.querySelector('.volume-icon');
                const volumeSlider = playerUI.querySelector('.volume-slider');

                const formatTime = (seconds) => {
                    if (isNaN(seconds)) return '0:00';
                    const minutes = Math.floor(seconds / 60);
                    const remainingSeconds = Math.floor(seconds % 60);
                    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
                };

                const updatePlayIcon = () => playPauseIcon.innerHTML = element.paused ? iconPaths.play : iconPaths.pause;
                const updateVolumeIcon = () => {
                    if (element.muted || element.volume === 0) volumeIcon.innerHTML = iconPaths.volumeMute;
                    else if (element.volume < 0.5) volumeIcon.innerHTML = iconPaths.volumeLow;
                    else volumeIcon.innerHTML = iconPaths.volumeHigh;
                };

                updatePlayIcon();
                updateVolumeIcon();

                playPauseButton.addEventListener('click', () => element.paused ? element.play() : element.pause());
                element.addEventListener('play', updatePlayIcon);
                element.addEventListener('pause', updatePlayIcon);
                element.addEventListener('ended', updatePlayIcon);

                element.addEventListener('loadedmetadata', () => timeDisplay.textContent = `${formatTime(0)} / ${formatTime(element.duration)}`);
                element.addEventListener('timeupdate', () => {
                    progressBar.style.width = `${(element.currentTime / element.duration) * 100}%`;
                    timeDisplay.textContent = `${formatTime(element.currentTime)} / ${formatTime(element.duration)}`;
                });

                progressBarWrapper.addEventListener('click', (e) => {
                    const rect = progressBarWrapper.getBoundingClientRect();
                    element.currentTime = ((e.clientX - rect.left) / rect.width) * element.duration;
                });

                volumeSlider.addEventListener('input', (e) => element.volume = e.target.value);
                element.addEventListener('volumechange', updateVolumeIcon);
                volumeButton.addEventListener('click', () => element.muted = !element.muted);

                element.parentNode.insertBefore(wrapper, element);
                wrapper.appendChild(element);
                wrapper.appendChild(playerUI);
                wrapper.appendChild(downloadButton);
            }
        });
    } catch (error) {
        console.error('Could not load ticket attachments: ', error);
    }
});