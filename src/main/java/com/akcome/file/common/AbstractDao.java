package com.akcome.file.common;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import com.akcome.common.MessageResolver;
import com.akcome.common.QueryData;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unchecked")
public abstract class AbstractDao<T> {
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private Class<T> clazz;

	public AbstractDao() {
		Type genType = this.getClass().getGenericSuperclass();
		if (genType instanceof ParameterizedType) {
			Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
			if (params != null && params.length > 0) {
				clazz = (Class<T>) params[0];
			}
		}
	}
	
	@Autowired
	protected MessageResolver msgResolver;

	@Autowired
	protected SessionFactory sessionFactory;

	protected Session getCurrentSession() {
		return sessionFactory.getCurrentSession();
	}

	public List<T> getAll() {
		return getAll(null);
	}

	public List<T> getAll(QueryData queryData) {
		StringBuilder hql = new StringBuilder("from ").append(clazz.getName());
		StringBuilder rowCountHql = new StringBuilder("select count(*) from ").append(clazz.getName());
		QueryData.PageData pageData = null;
		List<QueryData.Criterias> criterias = new ArrayList<QueryData.Criterias>();
		if (queryData != null) {
			pageData = queryData.getPageData();
			QueryData.Criterias cts = queryData.getCriterias();
			if (cts != null) {
				criterias.add(cts);
			}
			List<QueryData.Criterias> ctsList = queryData.getCriteriasList();
			if (ctsList != null && ctsList.size() > 0) {
				criterias.addAll(ctsList);
			}
		}
		if (criterias.size() > 0) {
			boolean hasWhere = false;
			int offset = 0;
			for (QueryData.Criterias cts : criterias) {
				List<QueryData.Criteria> ctrList = cts.getCriteriaList();
				if (ctrList != null && ctrList.size() > 0) {
					if (hasWhere) {
						hql.append(" and ");
						rowCountHql.append(" and ");
					} else {
						hql.append(" where ");
						rowCountHql.append(" where ");
						hasWhere = true;
					}
					hql.append("(");
					rowCountHql.append("(");
					for (int i = 0; i < ctrList.size(); i++) {
						if (i > 0) {
							hql.append(" ").append(cts.getOperator()).append(" ");
							rowCountHql.append(" ").append(cts.getOperator()).append(" ");
						}
						hql.append(ctrList.get(i).toSqlString(offset));
						rowCountHql.append(ctrList.get(i).toSqlString(offset));
						offset++;
					}
					hql.append(")");
					rowCountHql.append(")");
				}
			}
		}
		List<QueryData.OrderBy> orderbyList = null;
		if (queryData != null) {
			orderbyList = queryData.getOrderbyList();
		}
		if (orderbyList != null && !orderbyList.isEmpty()) {
			hql.append(" order by ");
			for (int i = 0; i < orderbyList.size(); i++) {
				QueryData.OrderBy orderby = orderbyList.get(i);
				hql.append(orderby.getProperty());
				if (!StringUtils.isEmpty(orderby.getDirectory())) {
					hql.append(" ").append(orderby.getDirectory());
				}
				if (i < orderbyList.size() - 1) {
					hql.append(",");
				}
			}
		}
		Query query = getCurrentSession().createQuery(hql.toString()).setReadOnly(true);
		Query rowCountQuery = getCurrentSession().createQuery(rowCountHql.toString());
		if (criterias.size() > 0) {
			int offset = 0;
			for (QueryData.Criterias cts : criterias) {
				List<QueryData.Criteria> ctrList = cts.getCriteriaList();
				if (ctrList != null && ctrList.size() > 0) {
					for (QueryData.Criteria ctr : ctrList) {
						if (QueryData.IN.equals(ctr.getOperator())) {
							query.setParameterList(ctr.getProperty().replace('.', '_') + offset,
									(Collection<?>) ctr.toSqlValue());
							rowCountQuery.setParameterList(ctr.getProperty().replace('.', '_') + offset,
									(Collection<?>) ctr.toSqlValue());
						} else {
							if (QueryData.IS_NULL.equals(ctr.getOperator())
									|| QueryData.IS_NOT_NULL.equals(ctr.getOperator())) {
								// do nothing
							} else {
								query.setParameter(ctr.getProperty().replace('.', '_') + offset, ctr.toSqlValue());
								rowCountQuery.setParameter(ctr.getProperty().replace('.', '_') + offset,
										ctr.toSqlValue());
							}
						}
						offset++;
					}
				}
			}

		}
		if (pageData != null) {
			query.setMaxResults(pageData.getLimit());
			query.setFirstResult(pageData.getStart());
		}
		if (queryData != null) {
			int rowCount = ((Number) rowCountQuery.uniqueResult()).intValue();
			queryData.setTotalCount(rowCount);
		}
		return query.setCacheable(true).list();
	}

	public T findById(final Long id) {
		return (T) getCurrentSession().get(clazz, id);
	}
	
	public List<?> findAll(Class<?> clz){
		return getCurrentSession().createCriteria(clz).list();
	}

	public void create(final T entity) {
		getCurrentSession().saveOrUpdate(entity);
	}

	public void update(final T entity) {
		getCurrentSession().update(entity);
	}

	public void save(T entity) {
		getCurrentSession().save(entity);
	}

	public void saveAny(Object entity) {
		getCurrentSession().saveOrUpdate(entity);
	}

	public void saveOrUpdate(T entity) {
		getCurrentSession().saveOrUpdate(entity);
	}

	public void delete(final T entity) {
		getCurrentSession().delete(entity);
	}

	public void deleteById(Number entityId) {
		getCurrentSession().createQuery("delete from " + clazz.getName() + " where id=:id").setParameter("id", entityId)
				.executeUpdate();
	}

	public T findByProperty(String prop, Object value) {
		List<T> results = getCurrentSession().createQuery("from " + clazz.getName() + " where " + prop + "=:prop")
				.setParameter("prop", value).list();
		if (results == null || results.isEmpty()) {
			return null;
		} else {
			return results.get(0);
		}
	}

}
