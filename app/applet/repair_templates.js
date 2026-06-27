const fs = require('fs');
const filePath = 'app/src/main/java/com/example/ui/screens/CustomerLedgerScreen.kt';

let content = fs.readFileSync(filePath, 'utf8').replace(/\r\n/g, '\n');

// Locate the start of generateDynamicTemplate function
const startPattern = 'fun generateDynamicTemplate(';
const startIdx = content.indexOf(startPattern);

// Locate the end of generateDynamicTemplate function, which is right before "fun saveCsvToDownloads"
const endPattern = 'fun saveCsvToDownloads(';
const endIdx = content.indexOf(endPattern);

if (startIdx === -1 || endIdx === -1) {
    console.log('Error: generateDynamicTemplate or saveCsvToDownloads not found.');
    process.exit(1);
}

console.log('Found start at:', startIdx, 'and end at:', endIdx);

const newFunction = `fun generateDynamicTemplate(
    index: Int,
    customerName: String,
    totalDue: Double,
    address: String?,
    todayStr: String,
    nextStr: String,
    shopName: String,
    shopPhone: String,
    ownerName: String,
    isBn: Boolean
): String {
    val displayAmount = if (isBn) toBengaliDigits(String.format("%.1f", totalDue)) else String.format("%.1f", totalDue)
    val locationText = if (address.isNullOrBlank() || address.trim() == "1" || address.trim() == "null") {
        if (isBn) "ঠিকানা: সংরক্ষিত নেই" else "Address: Not specified"
    } else {
        if (isBn) "ঠিকানা: \${address.trim()}" else "Address: \${address.trim()}"
    }

    val cleanShopName = if (shopName.isNotBlank() && shopName.trim() != "1" && shopName.trim() != "null") {
        shopName.trim()
    } else {
        if (isBn) "আমাদের প্রিয় প্রতিষ্ঠান" else "Our Store"
    }

    val owners = com.example.data.OwnerParser.deserialize(ownerName, shopPhone, "")
    val signatureBlock = if (owners.size <= 1) {
        val oName = owners.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: ownerName
        val oPhone = owners.firstOrNull()?.phone?.takeIf { it.isNotBlank() } ?: shopPhone
        if (isBn) {
            "দোকানদার: \$oName\\nমোবাইল: \$oPhone"
        } else {
            "Shopkeeper: \$oName\\nPhone: \$oPhone"
        }
    } else {
        if (isBn) {
            val details = owners.mapIndexed { idx, owner ->
                "মালিক \${idx + 1}: \${owner.name} (\${owner.phone})"
            }.joinToString("\\n")
            "দোকানের মালিকবৃন্দ:\\n\$details"
        } else {
            val details = owners.mapIndexed { idx, owner ->
                "Owner \${idx + 1}: \${owner.name} (\${owner.phone})"
            }.joinToString("\\n")
            "Shop Owners:\\n\$details"
        }
    }

    if (isBn) {
        return when (index % 5) {
            0 -> """
আসসালামু আলাইকুম, সম্মানিত \$customerName ভাই/বোন। (\$locationText)।

আশা করি ভালো আছেন। অত্যন্ত আনন্দের সাথে জানাচ্ছি যে, \$cleanShopName-এর সাথে আপনার পথচলা আমাদের জন্য অনেক গর্বের ও আনন্দের। আজকের তারিখ (\$todayStr) পর্যন্ত আপনার বর্তমান বাকির পরিমাণ হচ্ছে ৳\$displayAmount।
 
আমাদের ব্যবসা পরিচালনায় আপনার অমূল্য সহযোগিতা অব্যাহত রাখতে আগামী \$nextStr তারিখের মধ্যে (যা পরিশোধের শেষ তারিখ) এই বাকি টাকা পরিশোধ করার জন্য বিনীত অনুরোধ করছি। আপনার যেকোনো প্রয়োজনে আমরা পাশে আছি।
 
ধন্যবাদ ও আন্তরিক শুভেচ্ছা সহ -
\$signatureBlock
            """.trimIndent()
            
            1 -> """
আসসালামু আলাইকুম, প্রিয় \$customerName সাহেব। (\$locationText)।

আপনার ও আপনার পরিবারের উত্তরোত্তর চমৎকার সাফল্য ও সুস্বাস্থ্য কামনা করছি। \$cleanShopName-এ আজকের ডেট (\$todayStr) পর্যন্ত আপনার বর্তমান বাকির পরিমাণ হচ্ছে ৳\$displayAmount।
 
আমাদের মিষ্টি সম্পর্ক ও পারস্পরিক বিশ্বাসের খাতিরে আগামী \$nextStr তারিখের মধ্যে (যা পেমেন্টের শেষ তারিখ) এই বাকি টাকা পরিশোধ করার জন্য বিনীত অনুরোধ করছি।

শুভকামনায় -
\$signatureBlock
            """.trimIndent()
            
            2 -> """
আসসালামু আলাইকুম, শ্রদ্ধেয় \$customerName সাহেব। (\$locationText)।

আমাদের মধ্যকার ব্যবসায়িক আন্তরিক সম্পর্ক ও গভীর পারস্পরিক বিশ্বাসই আমাদের পথচলার মূল চাবিকাঠি। \$cleanShopName-এ আজকের তারিখ (\$todayStr) অনুযায়ী আপনার বর্তমান বাকির পরিমাণ হচ্ছে ৳\$displayAmount।

হিসাবটি হালনাগাদ করার জন্য আমাদের পক্ষ থেকে বিনীত নিবেদন, অনুগ্রহ করে আগামী \$nextStr তারিখের মধ্যে (যা পরিশোধের শেষ সময়) এই বাকি টাকা পরিশোধ করবেন।

আন্তরিক ধন্যবাদান্তে -
\$signatureBlock
            """.trimIndent()

            3 -> """
আসসালামু আলাইকুম, প্রিয় সুহৃদ \$customerName ভাই/বোন। (\$locationText)।

পরম করুণাময় আল্লাহর অশেষ রহমতে আশা করি সুস্থ ও নিরাপদে আছেন। \$cleanShopName-এ আজকের ডেট (\$todayStr) অনুযায়ী আপনার বর্তমান বাকির পরিমাণ হচ্ছে ৳\$displayAmount।

সময়ের সাথে সাথে আমাদের সুসম্পর্ক আরও সুদৃঢ় করতে আগামী \$nextStr তারিখের মধ্যে (যা পেমেন্টের লাস্ট ডেট) অনুগ্রহ করে এই বাকি টাকাটি পরিশোধ করার অনুরোধ করছি। আপনার সন্তুষ্টিই আমাদের লক্ষ্য।

শুভেচ্ছা ও শুভকামনায় -
\$signatureBlock
            """.trimIndent()
            
            else -> """
আসসালামু আলাইকুম, অত্যন্ত প্রিয় এবং সম্মানিত \$customerName। (\$locationText)।

আপনার মতো একজন সৎ ও আন্তরিক কাস্টমার পেয়ে আমাদের \$cleanShopName পরিবার সত্যিই অত্যন্ত ধন্য। আজকের তারিখ (\$todayStr) পর্যন্ত আপনার বর্তমান বাকির পরিমাণ হচ্ছে ৳\$displayAmount।

আপনার সুবিধাজনক সময়ে আগামী \$nextStr তারিখের মধ্যে (যা পেমেন্টের লাস্ট তারিখ) এই বাকি টাকা পরিশোধ করার জন্য বিনীতভাবে অনুরোধ জানাচ্ছি। আপনার এই সুন্দর ও সময়োপযোগী সহযোগিতার উচ্ছ্বসিত প্রশংসা করি।

বিনীত -
\$signatureBlock
            """.trimIndent()
        }
    } else {
        return when (index % 5) {
            0 -> """
Assalamu Alaikum, Dear \$customerName. (\$locationText).
                
We hope this text finds you well. It is an absolute honor having you as an esteemed customer of \$cleanShopName. Your current outstanding balance is ৳\$displayAmount.
                
Today is \$todayStr. To help us serve you with the best experience, we kindly request you to settle this balance by \$nextStr. Thank you for your continued cooperation.
                
Best regards -
\$signatureBlock
            """.trimIndent()
            
            1 -> """
Assalamu Alaikum, Respected \$customerName. (\$locationText).
                
Wishing you and your family abundant health. We highly appreciate your mutual trust and support. Your pending ledger balance at \$cleanShopName is ৳\$displayAmount.
                
You are kindly requested to clear the due by \$nextStr. (Today's Date: \$todayStr). Thank you for being a wonderful partner in our journey.
                
Respectfully -
\$signatureBlock
            """.trimIndent()
            
            2 -> """
Assalamu Alaikum, Dear friend \$customerName. (\$locationText).
                
We highly value our warm and trustworthy business relation. Currently, there is an open outstanding statement balance of ৳\$displayAmount recorded under your account at \$cleanShopName.
                
Today's date is \$todayStr. Please take a moment to clear this payment by \$nextStr to help us keep our stock fresh and services up-to-date.
                
Sincerely -
\$signatureBlock
            """.trimIndent()

            3 -> """
Assalamu Alaikum, Valued customer \$customerName. (\$locationText).
                
Hope you are having a productive and pleasant day. Our updated ledger shows a total outstanding due of ৳\$displayAmount at \$cleanShopName.
                
Today is \$todayStr. We would be profoundly grateful if you could clear this balance by the upcoming deadline of \$nextStr. We are always ready to assist you.
                
Warmest regards -
\$signatureBlock
            """.trimIndent()
            
            else -> """
Assalamu Alaikum, Honorable member \$customerName. (\$locationText).
                
Your partnership means the world to us at \$cleanShopName. Your current pending statement balance is ৳\$displayAmount.
                
As today's date is \$todayStr, we kindly and warmly remind you to clear this due by \$nextStr. We appreciate your stellar promptness and constant cooperation.
                
With appreciation -
\$signatureBlock
            """.trimIndent()
        }
    }
}

`;

content = content.substring(0, startIdx) + newFunction + content.substring(endIdx);
fs.writeFileSync(filePath, content, 'utf8');
console.log('Completed repair of generateDynamicTemplate function successfully!');
