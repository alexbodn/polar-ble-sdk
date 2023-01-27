package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.api.ble.exceptions.BleNotImplemented
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.*
import org.junit.Assert.assertThrows

internal class BlePmdClientTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePmdClient: BlePMDClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePmdClient = BlePMDClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected } returns true

        mockkObject(AccData)
        mockkObject(EcgData)
        mockkObject(GnssLocationData)
        mockkObject(GyrData)
        mockkObject(MagData)
        mockkObject(PpiData)
        mockkObject(PpgData)
        mockkObject(PressureData)
        mockkObject(TemperatureData)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `process not supported frame type`() {
        // Arrange
        // HEX: 01 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Measurement type                     01 (ppg data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           FF (compressed, frame type 0x7F)

        val locationDataFromService = byteArrayOf(
            0x01.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x7F.toByte(),
        )

        // Act && Assert
        assertThrows(BleNotImplemented::class.java) {
            blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)
        }
    }

    @Test
    fun `process ecg data`() {
        // Arrange
        // HEX: 00 38 6C 31 72 A4 D3 23 0D 03 00 12 03 11 10 04 00
        // index    type                                data
        // 0:      Measurement type                     00 (Ecg data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00 12 03 11 10 04 00
        val expectedTimeStamp = 946833049921875000uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val ecgDataHeaderFromService = byteArrayOf(
            0x00.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x03.toByte(),
        )
        val ecgDataFromService = byteArrayOf(
            0x00.toByte(), 0x12.toByte(), 0x03.toByte(),
            0x11.toByte(), 0x10.toByte(), 0x04.toByte(),
            0x00.toByte(),
        )

        val dataFromService = ecgDataHeaderFromService + ecgDataFromService
        val result = blePmdClient.monitorEcgNotifications(true)
        val testObserver = TestSubscriber<EcgData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            EcgData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            EcgData(timeStamp = frame.captured.timeStamp)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, dataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ecgData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, ecgData.timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ecgDataFromService, frame.captured.dataContent)
    }

    @Test
    fun `process ppg data`() {
        // Arrange
        // HEX: 01 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     01 (ppg data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val ppgDataHeaderFromService = byteArrayOf(
            0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
        )
        val ppgDataPartFromService = byteArrayOf(
            0xFF.toByte()
        )

        val ppgDataFromService = ppgDataHeaderFromService + ppgDataPartFromService

        val result = blePmdClient.monitorPpgNotifications(true)
        val testObserver = TestSubscriber<PpgData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            PpgData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            PpgData(timeStamp = frame.captured.timeStamp)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, ppgDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ppgData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, ppgData.timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ppgDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process acc data`() {
        // Arrange
        // HEX: 02 01 00 00 00 00 00 00 00 83 00
        // index    type                                data
        // 0:      Measurement type                     02 (acc data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 01 (0x0000000000000000 = 1)
        // 9:      Frame type                           83 (compressed, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 1uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val accDataHeaderFromService = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
        )
        val accDataPartFromService = byteArrayOf(
            0x00.toByte()
        )

        val accDataFromService = accDataHeaderFromService + accDataPartFromService
        val result = blePmdClient.monitorAccNotifications(true)
        val testObserver = TestSubscriber<AccData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            AccData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            AccData(timeStamp = frame.captured.timeStamp)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val accData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, accData.timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(accDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process ppi data`() {
        // Arrange
        // HEX: 03 00 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     03 (ppi data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00
        val expectedTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f

        val ppiDataHeaderFromService = byteArrayOf(
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x03.toByte(),
        )
        val ppiDataPartFromService = byteArrayOf(
            0x00.toByte()
        )

        val ppiDataFromService = ppiDataHeaderFromService + ppiDataPartFromService

        val result = blePmdClient.monitorPpiNotifications(true)
        val testObserver = TestSubscriber<PpiData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            PpiData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            PpiData()
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, ppiDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val ppiData = testObserver.values()[0]

        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(ppiDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process gyro data`() {
        // Arrange
        // HEX: 05 01 00 00 00 00 00 00 00 80 EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0:      Measurement type                     05 (gyro data)
        // 1..8:   64-bit Timestamp                     01 00 00 00 00 00 00 00 (0x0000000000000001 = 1)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10.. :  Data                                 EA FF 08 00 0D 00 03 01 DF 00
        val expectedTimeStamp = 1uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val gyroDataHeaderFromService = byteArrayOf(
            0x05.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val gyroDataPartFromService = byteArrayOf(
            0xEA.toByte(), 0xFF.toByte(),
            0x08.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x01.toByte(), 0xDF.toByte(), 0x00.toByte()
        )

        val gyroDataFromService = gyroDataHeaderFromService + gyroDataPartFromService

        val result = blePmdClient.monitorGyroNotifications(true)
        val testObserver = TestSubscriber<GyrData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            GyrData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            GyrData(timeStamp = frame.captured.timeStamp)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, gyroDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val gyroData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, gyroData.timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(gyroDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process magnetometer data`() {
        // Arrange
        // HEX: 06 01 00 00 00 00 00 00 70 80 FF
        // index    type                                data
        // 0:      Measurement type                     06 (magnetometer data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 70 (0x7000000000000000 = 8070450532247928832)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedTimeStamp = 8070450532247928832uL
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val magDataHeaderFromService = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x70.toByte(),
            0x80.toByte(),
        )

        val magDataPartFromService = byteArrayOf(
            0xFF.toByte()
        )

        val magDataFromService = magDataHeaderFromService + magDataPartFromService

        val result = blePmdClient.monitorMagnetometerNotifications(true)
        val testObserver = TestSubscriber<MagData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            MagData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            MagData(timeStamp = frame.captured.timeStamp)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, magDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        val magData = testObserver.values()[0]

        Assert.assertEquals(expectedTimeStamp, magData.timeStamp)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(magDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process location data`() {
        // Arrange
        // HEX: 0A 38 6C 31 72 A4 D3 23 0D 00 12
        // index    type                                data
        // 0:      Measurement type                     0A (Location data)
        // 1..8:   64-bit Timestamp                     38 6C 31 72 A4 D3 23 0D (0x0D23D3A472316C38 = 946833049921875000)
        // 9:      Frame type                           00 (raw, frame type 0)
        // 10:     Data                                 12
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = false
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val locationDataHeaderFromService = byteArrayOf(
            0x0A.toByte(),
            0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(),
            0x00.toByte(),
        )
        val locationDataPartFromService = byteArrayOf(
            0x12.toByte()
        )

        val locationDataFromService = locationDataHeaderFromService + locationDataPartFromService

        val result = blePmdClient.monitorLocationNotifications(true)
        val testObserver = TestSubscriber<GnssLocationData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            GnssLocationData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            GnssLocationData()
        }
        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, locationDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(locationDataPartFromService, frame.captured.dataContent)

    }

    @Test
    fun `process pressure data`() {
        // Arrange
        // HEX: 0B 00 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     0B (pressure data)
        // 1..8:   64-bit Timestamp                     00 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           03 (raw, frame type 3)
        // 10:     Data                                 00
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_3
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val pressureDataHeaderFromService = byteArrayOf(
            0x0B.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
        )
        val pressureDataPartFromService = byteArrayOf(
            0x00.toByte()
        )

        val pressureDataFromService = pressureDataHeaderFromService + pressureDataPartFromService

        val result = blePmdClient.monitorPressureNotifications(true)
        val testObserver = TestSubscriber<PressureData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            PressureData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            PressureData()
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, pressureDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)

        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(pressureDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `process temperature data`() {
        // Arrange
        // HEX: 0C 01 00 00 00 00 00 00 00 03 00
        // index    type                                data
        // 0:      Measurement type                     0C (temperature data)
        // 1..8:   64-bit Timestamp                     01 00 00 00 00 00 00 00 (0x0000000000000000 = 0)
        // 9:      Frame type                           80 (compressed, frame type 0)
        // 10:     Data                                 FF
        val expectedPreviousTimeStamp = 0uL
        val expectedIsCompressed = true
        val expectedFrameType = PmdDataFrame.PmdDataFrameType.TYPE_0
        val expectedFactor = 1.0f
        val expectedSampleRate = 0

        val temperatureDataHeaderFromService = byteArrayOf(
            0x0C.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val temperatureDataPartFromService = byteArrayOf(
            0xFF.toByte()
        )

        val temperatureDataFromService = temperatureDataHeaderFromService + temperatureDataPartFromService

        val result = blePmdClient.monitorTemperatureNotifications(true)
        val testObserver = TestSubscriber<TemperatureData>()
        result.subscribe(testObserver)

        val frame = slot<PmdDataFrame>()

        every {
            TemperatureData.parseDataFromDataFrame(
                frame = capture(frame)
            )
        } answers {
            TemperatureData()
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, temperatureDataFromService, 0, false)

        // Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)

        Assert.assertEquals(expectedPreviousTimeStamp, frame.captured.previousTimeStamp)
        Assert.assertEquals(expectedSampleRate, frame.captured.sampleRate)
        Assert.assertEquals(expectedIsCompressed, frame.captured.isCompressedFrame)
        Assert.assertEquals(expectedFrameType, frame.captured.frameType)
        Assert.assertEquals(expectedFactor, frame.captured.factor)
        Assert.assertArrayEquals(temperatureDataPartFromService, frame.captured.dataContent)
    }

    @Test
    fun `test previous timestamp`() {
        // Arrange
        // firstDataFromService: 02 01 00 00 00 00 00 00 00 83 00
        val accDataFromServiceFirst = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )
        // secondDataFromService: 02 FF FF FF FF FF FF FF F0 83 00
        val accDataFromServiceSecond = byteArrayOf(
            0x02.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )

        // randomDataFromService: 03 01 FF FF FF FF FF FF F0 83 00
        val randomDataFromService = byteArrayOf(
            0x03.toByte(),
            0x01.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )

        // thirdDataFromService: 02 03 00 00 00 00 00 00 00 83 00
        val accDataFromServiceThird = byteArrayOf(
            0x02.toByte(),
            0x03.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )

        val expectedTimeStampAfterFirstProcess = 1uL
        val expectedPreviousTimeStampAfterFirstProcess = 0uL

        val expectedTimeStampAfterSecondProcess = 0x0FFFFFFFFFFFFFFFuL
        val expectedPreviousTimeStampAfterSecondProcess = 1uL

        val expectedTimeStampAfterThirdProcess = 3uL
        val expectedPreviousTimeStampAfterThirdProcess = 0x0FFFFFFFFFFFFFFFuL

        val result = blePmdClient.monitorAccNotifications(true)
        val testObserver = TestSubscriber<AccData>()
        result.subscribe(testObserver)

        val capturedFrames = mutableListOf<PmdDataFrame>()
        every {
            AccData.parseDataFromDataFrame(
                frame = capture(capturedFrames)
            )
        } answers {
            AccData(timeStamp = 0uL)
        }

        // Act
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceFirst, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, randomDataFromService, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceSecond, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, randomDataFromService, 0, false)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromServiceThird, 0, false)

        // Assert
        testObserver.assertNoErrors()

        Assert.assertEquals(expectedTimeStampAfterFirstProcess, capturedFrames[0].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterFirstProcess, capturedFrames[0].previousTimeStamp)

        Assert.assertEquals(expectedTimeStampAfterSecondProcess, capturedFrames[1].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterSecondProcess, capturedFrames[1].previousTimeStamp)

        Assert.assertEquals(expectedTimeStampAfterThirdProcess, capturedFrames[2].timeStamp)
        Assert.assertEquals(expectedPreviousTimeStampAfterThirdProcess, capturedFrames[2].previousTimeStamp)
    }

    @Test
    fun `test previous timestamp reset`() {
        // Arrange
        val accDataFromService = byteArrayOf(
            0x02.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x83.toByte(),
            0x00.toByte()
        )

        val expectedTimeStampAfterFirstProcess = 1uL

        val result = blePmdClient.monitorAccNotifications(true)
        val testObserver = TestSubscriber<AccData>()
        result.subscribe(testObserver)

        val capturedFrames = mutableListOf<PmdDataFrame>()
        every {
            AccData.parseDataFromDataFrame(
                frame = capture(capturedFrames)
            )
        } answers {
            AccData(timeStamp = 0uL)
        }

        // Act & Assert
        val previousTimeStampAtTheBeginning = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC)
        blePmdClient.processServiceData(BlePMDClient.PMD_DATA, accDataFromService, 0, false)
        val previousTimeStampAfterProcess = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC)
        blePmdClient.reset()
        val previousTimeStampAfterReset = blePmdClient.getPreviousFrameTimeStamp(PmdMeasurementType.ACC)

        // Assert
        Assert.assertEquals(0uL, previousTimeStampAtTheBeginning)
        Assert.assertEquals(expectedTimeStampAfterFirstProcess, previousTimeStampAfterProcess)
        Assert.assertEquals(0uL, previousTimeStampAfterReset)
    }
}