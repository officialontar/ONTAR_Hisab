const fs = require('fs');
const filePath = 'app/src/main/java/com/example/viewmodel/AppViewModel.kt';

let content = fs.readFileSync(filePath, 'utf8').replace(/\r\n/g, '\n');

// Find the start of the corrupt block
const startPattern = 'val offlineReport = if (_isBengali.value) {';
const startIdx = content.indexOf(startPattern);

// Find the end of the corrupt block (which is '} else {' where the English block starts)
// To be safe, find the exact 'else {' that follows, or the start of the English section:
// 'val profitStatus = if (netProfit > 0) {'
const endPattern = 'val profitStatus = if (netProfit > 0) {\n                        "Net profit is ৳$netProfit which is encouraging.';
const endIdx = content.indexOf(endPattern);

if (startIdx === -1 || endIdx === -1) {
    console.log('Error: Could not locate the corrupt blocks in AppViewModel.kt', startIdx, endIdx);
    process.exit(1);
}

const replacement = `val offlineReport = if (_isBengali.value) {
                    val profitStatus = if (netProfit > 0) {
                        "মোট নিট লাভ হয়েছে ৳$netProfit যা খুবই উৎসাহব্যঞ্জক। এই ধারা অব্যাহত রাখুন এবং লাভ পুনরায় ব্যবসায় খাটান।"
                    } else if (netProfit < 0) {
                        "বর্তমানে আপনার ব্যবসায় কিছু লোকসান (৳\${java.lang.Math.abs(netProfit)}) দেখা যাচ্ছে। খরচ নিয়ন্ত্রণ বা পণ্যের দাম সমন্বয় করা জরুরি।"
                    } else {
                        "বর্তমানে ব্যবসায় উল্লেখযোগ্য লাভ বা ক্ষতি নেই। বিক্রি বাড়াতে নতুন পদক্ষেপ নিন।"
                    }

                    val stockStatus = if (lowStockItems.isNotEmpty()) {
                        "নিম্নলিখিত পণ্যগুলোর স্টক শেষ হতে চলেছে: \${lowStockItems.joinToString(\", \")}। দ্রুত স্টক পূরণ করুন।"
                    } else {
                        "স্টকের পণ্যের পরিমাণ সন্তোষজনক অবস্থায় রয়েছে।"
                    }

                    val dueStatus = if (totalCustomerDues > 0) {
                        "কাস্টমারদের কাছে মোট বকেয়া পাওনা রয়েছে ৳$totalCustomerDues। দ্রুত মূলধন বাড়াতে ‘বাকি খাতা’ থেকে তাদেরকে তাগাদা এসএমএস পাঠান।"
                    } else {
                        "কাস্টমারদের কাছে আপনার কোনো বকেয়া পাওনা নেই, এটি খুবই প্রশংসনীয় অর্থনৈতিক পরিচালনা।"
                    }

                    """
                    আসসালামু আলাইকুম! ✨ [স্মার্ট অফলাইন এআই ব্যাকআপ বিশ্লেষণ] ✨
                    গুগল এআই অনলাইন সার্ভার সাময়িকভাবে ব্যস্ত থাকায় নিচে আপনার লাইভ ব্যাকআপ রিপোর্ট শেয়ার করা হলো:

                    📊 আর্থিক স্বাস্থ্যের অবস্থা:
                    \$profitStatus

                    📦 পণ্য স্টক পর্যবেক্ষণ:
                    \$stockStatus

                    💰 বাকি বকেয়া পরামর্শ:
                    \$dueStatus

                    💡 আগামী দিনের জন্য স্মার্ট পরামর্শ:
                    ১. অধিক বিক্রিত পণ্যের স্টক সর্বদা সচল রাখুন ও অলাভজনক পণ্যে মূলধন আটকে রাখবেন না।
                    ২. ডিলার বা পাওনাদারদের সাথে হিসাব পরিষ্কার রাখুন এবং প্রয়োজনে বাকি খাতা থেকে ডিজিটাল অনুস্মারক ব্যবহার করুন।
                    """.trimIndent()
                } else {
                    `;

const newContent = content.substring(0, startIdx) + replacement + content.substring(endIdx);
fs.writeFileSync(filePath, newContent, 'utf8');
console.log('AppViewModel.kt corrupt segment successfully repaired!');
