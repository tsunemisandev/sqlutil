import java.awt.BorderLayout
import java.awt.Dimension
import java.io.FileInputStream
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Properties
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class DatabaseViewerApp : JFrame("Database Viewer") {

    private lateinit var properties: Properties
    private lateinit var url: String
    private lateinit var user: String
    private lateinit var password: String

    private val tablesComboBox = JComboBox<String>()
    private val searchTextField = JTextField(20)
    private val showInfoButton = JButton("Show Info")
    private val tableModel = DefaultTableModel()
    private val infoTable = JTable(tableModel)
    private val tabbedPane = JTabbedPane()

    private val insertTextArea = JTextArea(10, 40)
    private val updateTextArea = JTextArea(10, 40)
    private val generateDtoButton = JButton("Generate DTO")
    private val selectStatementTextArea = JTextArea(10, 40)
    private val dtoTextArea = JTextArea(10, 40)

    init {
        // Load properties from file
        loadProperties()

        // Set up the main frame
        defaultCloseOperation = EXIT_ON_CLOSE
        layout = BorderLayout()
        preferredSize = Dimension(800, 600)

        // Panel for controls
        val controlPanel = JPanel()
        controlPanel.add(JLabel("Tables:"))
        controlPanel.add(tablesComboBox)
        controlPanel.add(showInfoButton)

        // Add control panel to the frame
        add(controlPanel, BorderLayout.NORTH)

        // Add search field
        val searchPanel = JPanel()
        searchPanel.add(JLabel("Search:"))
        searchPanel.add(searchTextField)
        add(searchPanel, BorderLayout.SOUTH)

        // Tabbed pane setup
        tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT

        // Add Table Information tab
        val tableInfoPanel = JPanel(BorderLayout())
        tableInfoPanel.add(JScrollPane(infoTable), BorderLayout.CENTER)
        tabbedPane.addTab("Table Information", tableInfoPanel)

        // Add Basic SQL Statements tab
        val sqlStatementsPanel = JPanel()
        sqlStatementsPanel.layout = BoxLayout(sqlStatementsPanel, BoxLayout.Y_AXIS)

        val insertPanel = JPanel()
        insertPanel.add(JLabel("Insert Statement:"))
        insertPanel.add(JScrollPane(insertTextArea))
        sqlStatementsPanel.add(insertPanel)

        val updatePanel = JPanel()
        updatePanel.add(JLabel("Update Statement:"))
        updatePanel.add(JScrollPane(updateTextArea))
        sqlStatementsPanel.add(updatePanel)

        tabbedPane.addTab("Basic SQL Statements", sqlStatementsPanel)

        // Add Generate DTO tab
        val generateDtoPanel = JPanel(BorderLayout())

        val selectStatementPanel = JPanel()
        selectStatementPanel.add(JLabel("Paste SELECT Statement:"))
        selectStatementPanel.add(JScrollPane(selectStatementTextArea))
        generateDtoPanel.add(selectStatementPanel, BorderLayout.NORTH)

        val generateButtonPanel = JPanel()
        generateButtonPanel.add(generateDtoButton)
        generateDtoPanel.add(generateButtonPanel, BorderLayout.CENTER)

        val dtoPanel = JPanel()
        dtoPanel.add(JLabel("Generated DTO:"))
        dtoPanel.add(JScrollPane(dtoTextArea))
        generateDtoPanel.add(dtoPanel, BorderLayout.SOUTH)

        tabbedPane.addTab("Generate DTO", generateDtoPanel)

        // Add tabs to the main frame
        add(tabbedPane, BorderLayout.CENTER)

        // Load table names into ComboBox
        loadTableNames()

        // Event listeners
        tablesComboBox.addActionListener {
            val selectedTableName = tablesComboBox.selectedItem as String? ?: return@addActionListener
            loadTableInfo(selectedTableName)
            generateInsertStatement(selectedTableName)
            generateUpdateStatement(selectedTableName)
        }

        showInfoButton.addActionListener {
            val selectedTableName = tablesComboBox.selectedItem as String? ?: return@addActionListener
            loadTableInfo(selectedTableName)
            generateInsertStatement(selectedTableName)
            generateUpdateStatement(selectedTableName)
        }

        generateDtoButton.addActionListener {
            val selectStatement = selectStatementTextArea.text.trim()
            if (selectStatement.isNotEmpty()) {
                generateDtoFromSelect(selectStatement)
            }
        }

        searchTextField.document.addDocumentListener(object : DocumentListener {
            override fun changedUpdate(e: DocumentEvent?) {
                filterTables()
            }

            override fun insertUpdate(e: DocumentEvent?) {
                filterTables()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                filterTables()
            }
        })
    }

    private fun loadProperties() {
        properties = Properties()
        try {
            properties.load(FileInputStream("db.properties"))
            url = properties.getProperty("db.url")
            user = properties.getProperty("db.user")
            password = properties.getProperty("db.password")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadTableNames() {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url, user, password)
            val metaData = conn.metaData
            val tables = metaData.getTables(null, null, "%", arrayOf("TABLE"))

            while (tables.next()) {
                val tableName = tables.getString("TABLE_NAME")
                tablesComboBox.addItem(tableName)
            }
            tables.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            conn?.close()
        }
    }

    private fun loadTableInfo(tableName: String) {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url, user, password)
            val metaData = conn.metaData
            val columns = metaData.getColumns(null, null, tableName, "%")

            // Clear previous table data
            tableModel.setColumnCount(0)
            tableModel.setRowCount(0)

            // Add column headers
            tableModel.addColumn("Column Name")
            tableModel.addColumn("Type")
            tableModel.addColumn("Size")
            tableModel.addColumn("Scale") // Add scale information column

            // Add data rows
            while (columns.next()) {
                val columnName = columns.getString("COLUMN_NAME")
                val columnType = columns.getString("TYPE_NAME")
                val columnSize = columns.getInt("COLUMN_SIZE")
                println(columns)
                // Determine scale for numeric types
                var columnScale = ""
                if (columnType.uppercase() == "NUMERIC" || columnType.uppercase() == "DECIMAL") {
                    val scale = columns.getInt("DECIMAL_DIGITS")
                    columnScale = if (scale == 0) " " else scale.toString()
                }

                tableModel.addRow(arrayOf(columnName, columnType, columnSize, columnScale))
            }

            columns.close()

        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            conn?.close()
        }
    }

    private fun generateInsertStatement(tableName: String) {
        val insertStatement = StringBuilder()
        insertStatement.append("INSERT INTO $tableName (\n")

        val model = infoTable.model
        val columnNames = mutableListOf<String>()
        val paramNames = mutableListOf<String>()
        val jdbcTypes = mutableListOf<String>()

        for (i in 0 until model.rowCount) {
            val columnName = model.getValueAt(i, 0) as String
            val camelCaseColumnName = toCamelCase(columnName)
            val columnType = model.getValueAt(i, 1) as String
            columnNames.add(columnName)
            paramNames.add("#{item.$camelCaseColumnName}")
            jdbcTypes.add(getJdbcType(columnType))
        }

        insertStatement.append("  ${columnNames.joinToString(",\n  ") { it }}\n")
        insertStatement.append(") VALUES\n")
        insertStatement.append("<foreach collection=\"items\" item=\"item\" index=\"index\" separator=\",\">\n")
        insertStatement.append("  (\n")
        for (j in paramNames.indices) {
            insertStatement.append("    ${paramNames[j]},")
            insertStatement.append("    jdbcType=${jdbcTypes[j]}")
            if (j < paramNames.size - 1) {
                insertStatement.append(",\n")
            } else {
                insertStatement.append("\n")
            }
        }
        insertStatement.append("  )\n")
        insertStatement.append("</foreach>")

        insertTextArea.text = insertStatement.toString()
    }

    private fun generateUpdateStatement(tableName: String) {
        val updateStatement = StringBuilder()
        updateStatement.append("UPDATE $tableName\n")

        val model = infoTable.model
        var primaryKeyColumn: String? = null

        // Retrieve primary key information
        try {
            val conn = DriverManager.getConnection(url, user, password)
            val metaData = conn.metaData
            val primaryKey = metaData.getPrimaryKeys(null, null, tableName)
            if (primaryKey.next()) {
                primaryKeyColumn = primaryKey.getString("COLUMN_NAME")
            }
            primaryKey.close()
            conn.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }

        if (primaryKeyColumn != null) {
            updateStatement.append("<foreach collection=\"items\" item=\"item\" index=\"index\" separator=\";\">")
            updateStatement.append("UPDATE $tableName SET\n")

            val updateColumns = mutableListOf<String>()
            for (i in 0 until model.rowCount) {
                val columnName = model.getValueAt(i, 0) as String
                val camelCaseColumnName = toCamelCase(columnName)
                val columnType = model.getValueAt(i, 1) as String
                if (columnName == primaryKeyColumn) {
                    continue
                }
                updateColumns.add("$columnName = #{item.$camelCaseColumnName, jdbcType=${getJdbcType(columnType)}}")
            }

            updateStatement.append("  ${updateColumns.joinToString(",\n  ")}\n")
            updateStatement.append("WHERE $primaryKeyColumn = #{item.$primaryKeyColumn, jdbcType=${getJdbcType(primaryKeyColumn)}}")
            updateStatement.append("</foreach>")

        } else {
            updateStatement.append("<!-- Error: No primary key found for table $tableName -->")
        }

        updateTextArea.text = updateStatement.toString()
    }

    private fun generateDtoFromSelect(selectStatement: String) {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url, user, password)
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery(selectStatement)

            val metaData = resultSet.metaData
            val columns = metaData.columnCount

            val dtoClass = StringBuilder()
            dtoClass.append("data class GeneratedDTO (\n")

            for (i in 1..columns) {
                val columnName = metaData.getColumnLabel(i)
                val camelCaseColumnName = toCamelCase(columnName)
                val columnType = mapSqlTypeToKotlinType(metaData.getColumnType(i))
                dtoClass.append("    val $camelCaseColumnName: $columnType")
                if (i < columns) {
                    dtoClass.append(",\n")
                } else {
                    dtoClass.append("\n")
                }
            }

            dtoClass.append(")")

            dtoTextArea.text = dtoClass.toString()

            resultSet.close()
            statement.close()

        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            conn?.close()
        }
    }

    private fun mapSqlTypeToKotlinType(sqlType: Int): String {
        return when (sqlType) {
            java.sql.Types.INTEGER, java.sql.Types.TINYINT, java.sql.Types.SMALLINT -> "Int"
            java.sql.Types.BIGINT -> "Long"
            java.sql.Types.FLOAT, java.sql.Types.REAL -> "Float"
            java.sql.Types.DOUBLE -> "Double"
            java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> "BigDecimal"
            java.sql.Types.CHAR, java.sql.Types.VARCHAR, java.sql.Types.LONGVARCHAR -> "String"
            java.sql.Types.DATE -> "LocalDate"
            java.sql.Types.TIME -> "LocalTime"
            java.sql.Types.TIMESTAMP -> "LocalDateTime"
            java.sql.Types.BOOLEAN, java.sql.Types.BIT -> "Boolean"
            else -> "Any"
        }
    }

    private fun getJdbcType(sqlType: String): String {
        return when (sqlType.toUpperCase()) {
            "INTEGER", "INT" -> "INTEGER"
            "BIGINT" -> "BIGINT"
            "SMALLINT" -> "SMALLINT"
            "TINYINT" -> "TINYINT"
            "VARCHAR", "CHAR", "TEXT", "LONGVARCHAR" -> "VARCHAR"
            "NUMERIC", "DECIMAL" -> "NUMERIC"
            "BOOLEAN" -> "BIT"
            "DATE" -> "DATE"
            "TIME" -> "TIME"
            "TIMESTAMP" -> "TIMESTAMP"
            "FLOAT" -> "FLOAT"
            "DOUBLE" -> "DOUBLE"
            else -> "OTHER"
        }
    }

    private fun toCamelCase(columnName: String): String {
        // Split column name by underscore and convert to camel case
        val parts = columnName.split("_").mapIndexed { index, part ->
            if (index == 0) part else part.capitalize()
        }
        return parts.joinToString("")
    }

    private fun filterTables() {
        val keyword = searchTextField.text.trim().toLowerCase()

        // Clear current items in the combobox
        tablesComboBox.removeAllItems()

        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(url, user, password)
            val metaData = conn.metaData
            val tables = metaData.getTables(null, null, "%", arrayOf("TABLE"))

            while (tables.next()) {
                val tableName = tables.getString("TABLE_NAME")
                if (tableName.toLowerCase().contains(keyword)) {
                    tablesComboBox.addItem(tableName)
                }
            }
            tables.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            conn?.close()
        }
    }
}

fun main() {
    SwingUtilities.invokeLater {
        val app = DatabaseViewerApp()
        app.pack()
        app.isVisible = true
    }
}
