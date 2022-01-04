package io.github.triniwiz.fancycamera.barcodescanning

import android.graphics.Rect
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.*

class Result(barcode: Barcode) {

    val calendarEvent: CalenderEvent?
    val contactInfo: ContactInfo?
    val bounds: Bounds?
    val points: Array<Point>
    val displayValue: String?
    val driverLicense: DriverLicense?
    val email: Email?
    val format: BarcodeScanner.BarcodeFormat
    val geoPoint: GeoPoint?
    val phone: Phone?
    val rawBytes: ByteArray?
    val rawValue: String?
    val sms: Sms?
    val url: UrlBookmark?
    val valueType: ValueType
    val wifi: WiFi?

    class WiFi(wifi: Barcode.WiFi) {
        val encryptionType: EncryptionType
        val password = wifi.password
        val ssid = wifi.ssid

        init {
            encryptionType = when (wifi.encryptionType) {
                1 -> EncryptionType.Open
                2 -> EncryptionType.WPA
                3 -> EncryptionType.WEP
                else -> EncryptionType.Unknown
            }
        }

        enum class EncryptionType(val type: String) {
            Open("open"),
            WPA("wpa"),
            WEP("wep"),
            Unknown("unknown")
        }
    }

    class UrlBookmark(bookMark: Barcode.UrlBookmark) {
        val title = bookMark.title
        val url = bookMark.url
    }

    class Sms(sms: Barcode.Sms) {
        val message = sms.message
        val phoneNumber = sms.phoneNumber
    }

    class Phone(phone: Barcode.Phone) {
        val number = phone.number
        val type: Type

        init {
            type = when (phone.type) {
                0 -> Type.Unknown
                1 -> Type.Home
                2 -> Type.Work
                3 -> Type.Fax
                4 -> Type.Mobile
                else -> Type.Unknown
            }
        }

        enum class Type(val typeName: String) {
            Unknown("unknown"),
            Home("home"),
            Work("work"),
            Fax("fax"),
            Mobile("mobile")
        }
    }

    class Email(email: Barcode.Email) {
        val address = email.address
        val subject = email.subject
        val body = email.body
        val type: Type

        init {
            type = when (email.type) {
                0 -> Type.Unknown
                1 -> Type.Home
                2 -> Type.Work
                else -> Type.Unknown
            }
        }

        enum class Type(val typeName: String) {
            Unknown("unknown"),
            Home("home"),
            Work("work")
        }
    }

    class DriverLicense(driverLicense: Barcode.DriverLicense) {
        val documentType = driverLicense.documentType
        var firstName = driverLicense.firstName
        var middleName = driverLicense.middleName
        var lastName = driverLicense.lastName
        var gender = driverLicense.gender
        var addressStreet = driverLicense.addressStreet
        var addressCity = driverLicense.addressCity
        var addressState = driverLicense.addressState
        var addressZip = driverLicense.addressZip
        var licenseNumber = driverLicense.licenseNumber
        var issueDate = driverLicense.issueDate
        var expiryDate = driverLicense.expiryDate
        var birthDate = driverLicense.birthDate
        var issuingCountry = driverLicense.issuingCountry
    }


    class CalenderEvent(event:
                        Barcode.CalendarEvent) {
        val description = event.description
        val location = event.location
        val organizer = event.organizer
        val status = event.status
        val summary = event.summary
        val start = event.start?.let { Date(it) }
        val end = event.end?.let { Date(it) }

        class Date(date: Barcode.CalendarDateTime) {
            val seconds = date.seconds
            val day = date.day
            val hours = date.hours
            val isUtc = date.isUtc
            val minutes = date.minutes
            val month = date.month
            val year = date.year
            val rawValue = date.rawValue
            val date: java.util.Date

            init {
                val calender = Calendar.getInstance()
                calender.clear()
                calender.set(year, month, day, hours, minutes, seconds)
                this.date = calender.time
            }
        }


    }

    class ContactInfo(info: Barcode.ContactInfo) {
        val addresses: Array<Address>

        init {
            val addressList = mutableListOf<Address>()
            for (address in info.addresses) {
                addressList.add(
                        Address(address)
                )
            }
            addresses = addressList.toTypedArray()
        }


        class Address(address: Barcode.Address) {
            val addressLines = address.addressLines
            val type: Type

            init {
                type = when (address.type) {
                    0 -> Type.Unknown
                    1 -> Type.Home
                    2 -> Type.Work
                    else -> Type.Unknown
                }
            }

            enum class Type(val typeName: String) {
                Unknown("unknown"),
                Home("home"),
                Work("work")
            }
        }
    }

    class Bounds(rect: Rect) {
        class Origin(val x: Int, val y: Int)
        class Size(val width: Int,
                   val height: Int)

        val origin = Origin(rect.left, rect.right)
        val size = Size(rect.width(), rect.height())

    }

    class Point(point: android.graphics.Point) {
        val x: Int = point.x

        val y: Int = point.y
    }

    class GeoPoint(point: Barcode.GeoPoint) {
        val lat: Double = point.lat

        val lng: Double = point.lng
    }

    enum class ValueType(val type: String) {
        ContactInfo("contactInfo"),
        Email("email"),
        ISBN("isbn"),
        Phone("phone"),
        Product("product"),
        Text("text"),
        Sms("sms"),
        URL("url"),
        WiFi("wifi"),
        Geo("geo"),
        CalenderEvent("calender"),
        DriverLicense("driverLicense"),
        Unknown("unknown")
    }

    init {
        this.bounds = barcode.boundingBox?.let { Bounds(it) }
        this.displayValue = barcode.displayValue
        this.driverLicense = barcode.driverLicense?.let { DriverLicense(it) }
        this.email = barcode.email?.let { Email(it) }
        this.geoPoint = barcode.geoPoint?.let { GeoPoint(it) }
        this.phone = barcode.phone?.let { Phone(it) }
        this.rawBytes = barcode.rawBytes
        this.rawValue = barcode.rawValue
        this.sms = barcode.sms?.let { Sms(it) }
        this.url = barcode.url?.let { UrlBookmark(it) }
        this.valueType = when (barcode.valueType) {
            Barcode.TYPE_CALENDAR_EVENT -> ValueType.CalenderEvent
            Barcode.TYPE_CONTACT_INFO -> ValueType.ContactInfo
            Barcode.TYPE_EMAIL -> ValueType.Email
            Barcode.TYPE_ISBN -> ValueType.ISBN
            Barcode.TYPE_PHONE -> ValueType.Phone
            Barcode.TYPE_PRODUCT -> ValueType.Product
            Barcode.TYPE_SMS -> ValueType.Sms
            Barcode.TYPE_TEXT -> ValueType.Text
            Barcode.TYPE_URL -> ValueType.URL
            Barcode.TYPE_WIFI -> ValueType.WiFi
            Barcode.TYPE_GEO -> ValueType.Geo
            Barcode.TYPE_DRIVER_LICENSE -> ValueType.DriverLicense
            else -> ValueType.Unknown
        }
        this.wifi = barcode.wifi?.let { WiFi(it) }
        calendarEvent = barcode.calendarEvent?.let { CalenderEvent(it) }
        contactInfo = barcode.contactInfo?.let { ContactInfo(it) }
        val points = mutableListOf<Point>()
        for (point in barcode.cornerPoints ?: arrayOf()) {
            points.add(Point(point))
        }
        this.points = points.toTypedArray()
        format = BarcodeScanner.BarcodeFormat.fromBarcode(barcode.format)
                ?: BarcodeScanner.BarcodeFormat.ALL
    }
}