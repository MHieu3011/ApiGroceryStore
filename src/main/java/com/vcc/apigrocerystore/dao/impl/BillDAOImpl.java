package com.vcc.apigrocerystore.dao.impl;

import com.vcc.apigrocerystore.dao.BillDAO;
import com.vcc.apigrocerystore.entities.BillEntity;
import com.vcc.apigrocerystore.exception.CommonException;
import com.vcc.apigrocerystore.factory.MySQLConnectionFactory;
import com.vcc.apigrocerystore.global.ErrorCode;
import com.vcc.apigrocerystore.model.request.BillDetailRegistrationFormRequest;
import com.vcc.apigrocerystore.model.response.InfoBillDetailResponse;
import com.vcc.apigrocerystore.model.response.InfoBillResponse;
import com.vcc.apigrocerystore.model.response.InfoTotalRevenueByBrand;
import com.vcc.apigrocerystore.utils.DateTimeUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BillDAOImpl extends AbstractDAO implements BillDAO {

    @Override
    public void createByParam(BillEntity entity) throws Exception {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = MySQLConnectionFactory.getInstance().getMySQLConnection();
            connection.setAutoCommit(false);
            String sql = "INSERT INTO bill(idcustomer, iduser, date, totalmoney) VALUES(?, ?, ?, ?)";
            statement = connection.prepareStatement(sql);
            statement.setLong(1, entity.getIdCustomer());
            statement.setLong(2, entity.getIdUser());
            statement.setLong(3, entity.getDate());
            statement.setLong(4, entity.getTotalMoney());
            statement.executeUpdate();
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                connection.rollback();
            }
            eLogger.error("Error BillDAO.insert bill: {}", e.getMessage());
        } finally {
            releaseConnectAndStatement(connection, statement);
        }
    }

    @Override
    public InfoBillResponse create(long idCustomer, long idUser, long date, List<BillDetailRegistrationFormRequest> billDetails) throws Exception {
        InfoBillResponse result = new InfoBillResponse();
        List<InfoBillDetailResponse> billDetailList = new ArrayList<>();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = MySQLConnectionFactory.getInstance().getMySQLConnection();
            connection.setAutoCommit(false);
            //Thêm mới 1 hóa đơn với totalMoney = 0
            String sql = "INSERT INTO bill(idcustomer, iduser, date, totalmoney) VALUES(?, ?, ?, ?)";
            statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            statement.setLong(1, idCustomer);
            statement.setLong(2, idUser);
            statement.setLong(3, date);
            statement.setLong(4, 0L);
            statement.executeUpdate();
            resultSet = statement.getGeneratedKeys();
            long idBill = 0L;
            while (resultSet.next()) {
                idBill = resultSet.getLong(1);
            }
            //Thêm mới các chi tiết hóa đơn (item cùng với số lượng của nó), có kiểm tra xem còn hàng trong kho hay không
            for (BillDetailRegistrationFormRequest billDetail : billDetails) {
                long idItem = billDetail.getIdItem();
                int number = billDetail.getNumber();
                String sql1 = "UPDATE storehouse s SET s.number = s.number - ? WHERE s.iditem = ? AND s.number >= ? LIMIT 1";
                statement = connection.prepareStatement(sql1);
                statement.setInt(1, number);
                statement.setLong(2, idItem);
                statement.setInt(3, number);
                int check = statement.executeUpdate();
                if (check > 0) {
                    String sql2 = "INSERT INTO billdetail(idbill, iditem, number) VALUES (?, ?, ?)";
                    statement = connection.prepareStatement(sql2);
                    statement.setLong(1, idBill);
                    statement.setLong(2, idItem);
                    statement.setInt(3, number);
                    statement.executeUpdate();
                    InfoBillDetailResponse infoBillDetail = new InfoBillDetailResponse();
                    infoBillDetail.setNumber(billDetail.getNumber());
                    infoBillDetail.setIdItem(billDetail.getIdItem());
                    billDetailList.add(infoBillDetail);
                } else {
                    throw new CommonException(ErrorCode.NO_MORE_ITEM_IN_STORE_HOUSE, "No more items in store house");
                }
            }
            //Cập nhật lại totalMoney cho hóa đơn
            StringBuilder sql3 = new StringBuilder("UPDATE bill b SET b.totalmoney =");
            sql3.append(" (SELECT SUM((bd.number*i.price)) total");
            sql3.append(" FROM billdetail bd");
            sql3.append(" INNER JOIN item i ON bd.iditem = i.id");
            sql3.append(" WHERE bd.idbill = ?");
            sql3.append(" GROUP BY bd.idbill)");
            sql3.append(" WHERE id = ?");
            statement = connection.prepareStatement(sql3.toString());
            statement.setLong(1, idBill);
            statement.setLong(2, idBill);
            statement.executeUpdate();
            //Thêm hóa đơn thành công thì trả về dữ liệu hóa dơn đã thêm
            result.setIdCustomer(idCustomer);
            result.setIdUser(idUser);
            result.setDate(DateTimeUtils.formatTimeInSec(date, DateTimeUtils.DEFAULT_DATE_FORMAT));
            result.setBillDetails(billDetailList);
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                connection.rollback();
            }
            eLogger.error("Error BillDAO.insert bill: {}", e.getMessage());
        } finally {
            releaseResource(connection, statement, resultSet);
        }
        return result;
    }

    @Override
    public InfoTotalRevenueByBrand getTotalRevenueByBrand(long fromDate, long toDate, String brand) throws Exception {
        InfoTotalRevenueByBrand result = new InfoTotalRevenueByBrand();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = MySQLConnectionFactory.getInstance().getMySQLConnection();
            StringBuilder sql = new StringBuilder("SELECT i.brand, sum((bd.number*i.price)) totalrevenue");
            sql.append(" FROM billdetail bd");
            sql.append(" INNER JOIN bill b ON bd.idbill = b.id");
            sql.append(" INNER JOIN item i ON bd.iditem = i.id");
            sql.append(" WHERE b.date BETWEEN ? AND ? AND i.brand = ?");
            sql.append(" GROUP BY i.brand");
            statement = connection.prepareStatement(sql.toString());
            statement.setLong(1, fromDate);
            statement.setLong(2, toDate);
            statement.setString(3, brand);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.setBrand(resultSet.getString("brand"));
                result.setTotalRevenue(resultSet.getLong("totalrevenue"));
            }
        } catch (Exception e) {
            eLogger.error("Error BillDAO.getTotalRevenueByBrand bill: {}", e.getMessage());
        } finally {
            releaseResource(connection, statement, resultSet);
        }
        return result;
    }
}
